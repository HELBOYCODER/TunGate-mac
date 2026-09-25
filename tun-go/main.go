// tungatun: userspace AmneziaWG/WireGuard tunnel for macOS.
// Runs as root (via the privileged helper). Creates a utun, applies the
// interface addresses, drives amneziawg-go over it, and installs the
// AllowedIPs routes (wg-quick style, with endpoint bypass + half-routes).
package main

import (
	"bufio"
	"fmt"
	"net"
	"os"
	"os/exec"
	"os/signal"
	"encoding/base64"
	"encoding/hex"
	"strconv"
	"strings"
	"syscall"

	"github.com/amnezia-vpn/amneziawg-go/v3/conn"
	"github.com/amnezia-vpn/amneziawg-go/v3/device"
	"github.com/amnezia-vpn/amneziawg-go/v3/tun"
)

type peerCfg struct {
	publicKey   string
	preshared   string
	endpoint    string
	allowedIPs  []string
	jc, jmin    string
	jmax        string
	s1, s2      string
	h1, h2      string
}

type ifaceCfg struct {
	privateKey string
	addresses  []string
	mtu        int
	listenPort string
	// device-level amnezia params (client profiles carry them under [Peer])
	jc, jmin, jmax, s1, s2, h1, h2 string
	peers                          []peerCfg
}

// WireGuard keys are base64 in .conf files; the uapi expects hex.
func keyHex(s string) string {
	raw, err := base64.StdEncoding.DecodeString(strings.TrimSpace(s))
	if err != nil {
		return s
	}
	return hex.EncodeToString(raw)
}

func run(name string, args ...string) error {
	cmd := exec.Command(name, args...)
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("%s %s: %w (%s)", name, strings.Join(args, " "), err, strings.TrimSpace(string(out)))
	}
	return nil
}

func parseConf(path string) (*ifaceCfg, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()

	cfg := &ifaceCfg{mtu: 1420}
	var cur *peerCfg
	sc := bufio.NewScanner(f)
	set := func(dst *string, v string) { if v != "" { *dst = v } }
	for sc.Scan() {
		line := strings.TrimSpace(sc.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		if strings.HasPrefix(line, "[") {
			if strings.HasPrefix(strings.ToLower(line), "[peer") {
				cfg.peers = append(cfg.peers, peerCfg{})
				cur = &cfg.peers[len(cfg.peers)-1]
			} else {
				cur = nil
			}
			continue
		}
		kv := strings.SplitN(line, "=", 2)
		if len(kv) != 2 {
			continue
		}
		k := strings.ToLower(strings.TrimSpace(kv[0]))
		v := strings.TrimSpace(kv[1])
		switch k {
		case "privatekey":
			cfg.privateKey = v
		case "address":
			for _, a := range strings.Split(v, ",") {
				cfg.addresses = append(cfg.addresses, strings.TrimSpace(a))
			}
		case "mtu":
			if n, e := strconv.Atoi(v); e == nil {
				cfg.mtu = n
			}
		case "listenport":
			cfg.listenPort = v
		case "publickey":
			if cur != nil {
				cur.publicKey = v
			}
		case "presharedkey":
			if cur != nil {
				cur.preshared = v
			}
		case "endpoint":
			if cur != nil {
				cur.endpoint = v
			}
		case "allowedips":
			if cur != nil {
				for _, a := range strings.Split(v, ",") {
					cur.allowedIPs = append(cur.allowedIPs, strings.TrimSpace(a))
				}
			}
		case "jc":
			set(&cfg.jc, v)
			if cur != nil {
				set(&cur.jc, v)
			}
		case "jmin":
			set(&cfg.jmin, v)
			if cur != nil {
				set(&cur.jmin, v)
			}
		case "jmax":
			set(&cfg.jmax, v)
			if cur != nil {
				set(&cur.jmax, v)
			}
		case "s1":
			set(&cfg.s1, v)
		case "s2":
			set(&cfg.s2, v)
		case "h1":
			set(&cfg.h1, v)
		case "h2":
			set(&cfg.h2, v)
		}
	}
	if cfg.privateKey == "" || len(cfg.peers) == 0 {
		return nil, fmt.Errorf("invalid config: missing PrivateKey or Peer")
	}
	return cfg, nil
}

// defaultGateway returns the physical gateway IP for endpoint-bypass routes.
func defaultGateway() net.IP {
	out, err := exec.Command("route", "-n", "get", "default").Output()
	if err == nil {
		for _, line := range strings.Split(string(out), "\n") {
			if fields := strings.Fields(line); len(fields) == 2 && fields[0] == "gateway:" {
				if ip := net.ParseIP(fields[1]); ip != nil {
					return ip
				}
			}
		}
	}
	for _, iface := range []string{"en0", "en1", "en2", "en3", "en4", "en5"} {
		out, err := exec.Command("ipconfig", "getoption", iface, "router").Output()
		if err == nil {
			if ip := net.ParseIP(strings.TrimSpace(string(out))); ip != nil {
				return ip
			}
		}
	}
	return nil
}

func splitCidr(cidr string) (ip, ones string, v6 bool, err error) {
	if !strings.Contains(cidr, "/") {
		if net.ParseIP(cidr) == nil {
			return "", "", false, fmt.Errorf("not an ip: %s", cidr)
		}
		if strings.Contains(cidr, ":") {
			return cidr, "128", true, nil
		}
		return cidr, "32", false, nil
	}
	_, n, e := net.ParseCIDR(cidr)
	if e != nil {
		return "", "", false, e
	}
	onesBit, _ := n.Mask.Size()
	return n.IP.String(), strconv.Itoa(onesBit), n.IP.To4() == nil, nil
}

// wg-quick trick: a default capture becomes two half-space routes so the
// pre-existing default route (and endpoint bypass hosts) keep working.
func expandRoutes(cidr string) []string {
	ip, ones, v6, err := splitCidr(cidr)
	if err != nil {
		return nil
	}
	if !v6 && ip == "0.0.0.0" && ones == "0" {
		return []string{"0.0.0.0/1", "128.0.0.0/1"}
	}
	if v6 && ip == "::" && ones == "0" {
		return []string{"::/1", "8000::/1"}
	}
	return []string{ip + "/" + ones}
}

func addRoute(cidr, iface string) error {
	var added []string
	var lastErr error
	for _, r := range expandRoutes(cidr) {
		parts := strings.SplitN(r, "/", 2)
		fam := "-net"
		if strings.Contains(parts[0], ":") {
			fam = "-inet6"
		}
		if err := run("route", "-n", "add", fam, r, "-interface", iface); err != nil {
			lastErr = err
		} else {
			added = append(added, r)
		}
	}
	_ = added
	return lastErr
}

func delRoute(cidr, iface string) {
	for _, r := range expandRoutes(cidr) {
		parts := strings.SplitN(r, "/", 2)
		fam := "-net"
		if strings.Contains(parts[0], ":") {
			fam = "-inet6"
		}
		_ = run("route", "-n", "delete", fam, r, "-interface", iface)
	}
}

func main() {
	if len(os.Args) < 2 {
		fmt.Fprintln(os.Stderr, "usage: tungatun <amnezia|wireguard .conf> [stateFile]")
		os.Exit(2)
	}
	cfg, err := parseConf(os.Args[1])
	if err != nil {
		fmt.Fprintln(os.Stderr, "conf:", err)
		os.Exit(1)
	}

	stateFile := ""
	if len(os.Args) > 2 {
		stateFile = os.Args[2]
	}

	tunDev, err := tun.CreateTUN("utun", cfg.mtu)
	if err != nil {
		fmt.Fprintln(os.Stderr, "tun:", err)
		os.Exit(1)
	}
	tunName, err := tunDev.Name()
	if err != nil || tunName == "" {
		tunName = "utun"
	}

	// Interface addressing on macOS utun (point-to-point style).
	var addedRoutes []string
	applyAddress := func(cidr string) {
		ip, ones, v6, err := splitCidr(cidr)
		if err != nil {
			return
		}
		if v6 {
			_ = run("ifconfig", tunName, "addinet6", ip, ip, "prefixlen", ones, "alias")
		} else {
			n, e := strconv.Atoi(ones)
			if e != nil {
				return
			}
			mask := uint32(0xffffffff) << uint(32-n)
			dotted := fmt.Sprintf("%d.%d.%d.%d", mask>>24&0xff, mask>>16&0xff, mask>>8&0xff, mask&0xff)
			_ = run("ifconfig", tunName, "inet", ip, ip, "netmask", dotted, "alias")
		}
	}
	_ = run("ifconfig", tunName, "mtu", strconv.Itoa(cfg.mtu))
	for _, a := range cfg.addresses {
		applyAddress(a)
	}
	_ = run("ifconfig", tunName, "up")

	logger := device.NewLogger(device.LogLevelError, "tungatun: ")
	dev := device.NewDevice(tunDev, conn.NewDefaultBind(), logger)

	var uapi strings.Builder
	add := func(kv string) { uapi.WriteString(kv); uapi.WriteString("\n") }
	add("private_key=" + keyHex(cfg.privateKey))
	if cfg.listenPort != "" {
		add("listen_port=" + cfg.listenPort)
	}
	for _, kv := range []struct{ k, v string }{
		{"jc", cfg.jc}, {"jmin", cfg.jmin}, {"jmax", cfg.jmax},
		{"s1", cfg.s1}, {"s2", cfg.s2}, {"h1", cfg.h1}, {"h2", cfg.h2},
	} {
		if kv.v != "" {
			add(kv.k + "=" + kv.v)
		}
	}
	add("replace_peers=true")
	for _, p := range cfg.peers {
		add("public_key=" + keyHex(p.publicKey))
		if p.preshared != "" {
			add("preshared_key=" + keyHex(p.preshared))
		}
		if p.endpoint != "" {
			add("endpoint=" + p.endpoint)
			host := p.endpoint
			if h, _, e := net.SplitHostPort(p.endpoint); e == nil {
				host = h
			}
			if gw := defaultGateway(); gw != nil {
				if ips, e := net.LookupHost(host); e == nil {
					for _, ip := range ips {
						_ = run("route", "-n", "add", "-host", ip, gw.String())
						addedRoutes = append(addedRoutes, "bypass:"+ip)
					}
				}
			}
		}
		for _, a := range p.allowedIPs {
			for _, r := range expandRoutes(a) {
				add("allowed_ip=" + r)
			}
		}
	}
	if err := dev.IpcSet(uapi.String()); err != nil {
		fmt.Fprintln(os.Stderr, "uapi:", err)
	}

	// Routes for allowed IPs (the uapi call above only programs the crypto plane).
	for _, p := range cfg.peers {
		for _, a := range p.allowedIPs {
			for _, r := range expandRoutes(a) {
				fam := "-net"
				if strings.Contains(strings.SplitN(r, "/", 2)[0], ":") {
					fam = "-inet6"
				}
				if run("route", "-n", "add", fam, r, "-interface", tunName) == nil {
					addedRoutes = append(addedRoutes, "exact:"+r)
				}
			}
		}
	}

	if err := dev.Up(); err != nil {
		fmt.Fprintln(os.Stderr, "up:", err)
	}

	if stateFile != "" {
		_ = os.WriteFile(stateFile, []byte(tunName), 0o644)
	}
	fmt.Println("READY", tunName)

	sig := make(chan os.Signal, 1)
	signal.Notify(sig, syscall.SIGINT, syscall.SIGTERM)
	<-sig

	dev.Close()
	for _, r := range addedRoutes {
		switch {
		case strings.HasPrefix(r, "bypass:"):
			_ = run("route", "-n", "delete", "-host", strings.TrimPrefix(r, "bypass:"))
		case strings.HasPrefix(r, "exact:"):
			e := strings.TrimPrefix(r, "exact:")
			fam := "-net"
			if strings.Contains(strings.SplitN(e, "/", 2)[0], ":") {
				fam = "-inet6"
			}
			_ = run("route", "-n", "delete", fam, e, "-interface", tunName)
		default:
			delRoute(r, tunName)
		}
	}
	if stateFile != "" {
		_ = os.Remove(stateFile)
	}
}
