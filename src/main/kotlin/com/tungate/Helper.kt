package com.tungate

import com.tungate.Logs.log
import java.io.File
import java.util.UUID

private const val DAEMON_LABEL = "com.tungate.helper"
private const val DAEMON_PLIST_PATH = "/Library/LaunchDaemons/$DAEMON_LABEL.plist"

/**
 * Root helper (LaunchDaemon, one admin approval that survives reinstalls) that runs
 * the userspace AmneziaWG engine as root and manages per-tunnel DNS.
 */
object Helper {

    private val dir get() = Paths.helperDir
    private val cmdFile get() = File(dir, "cmd")
    private val doneFile get() = File(dir, "cmd.done")
    private val stopFile get() = File(dir, "stop")
    private val startedFile get() = File(dir, "watcher.started")
    private val scriptFile get() = File(dir, "tungate-helper.sh")
    private val plistFile get() = File(dir, "tungate-helper.plist")
    private val confPathFile get() = File(dir, "current_conf")
    private val dnsFile get() = File(dir, "dns_servers")
    private val toolFile get() = File(dir, "tun_tool")
    private val utunNameFile get() = File(dir, "utun.name")
    private val heartbeatFile get() = File(dir, "heartbeat")

    @Volatile
    private var promptedForAdmin = false

    fun adminAskedBefore(): Boolean = promptedForAdmin

    private fun watcherAlive(): Boolean {
        val printOut = runCatching {
            ProcessBuilder("launchctl", "print", "system/$DAEMON_LABEL")
                .redirectErrorStream(true).start().inputStream.bufferedReader().readText()
        }.getOrDefault("")
        return "state = running" in printOut
    }

    private fun writeScript(): Boolean {
        val desired = """
            #!/bin/sh
            # TunGate privileged helper - generated, do not edit.
            PATH="/usr/bin:/bin:/usr/sbin:/sbin"; export PATH
            DIR="${dir.absolutePath}"

            active_service() {
              IF=`route -n get default 2>/dev/null | awk '/interface:/{print ${'$'}2}'`
              case "${'$'}IF" in en*) ;; *) IF=`netstat -rn -f inet | awk '${'$'}1=="default" && ${'$'}NF ~ /^en/ {print ${'$'}NF; exit}'`;; esac
              [ -z "${'$'}IF" ] && return
              networksetup -listnetworkserviceorder | awk -v ifc="${'$'}IF" '
                /^\([0-9]+\)/ { name=${'$'}0; sub(/^[^)]*\) */, "", name) }
                index(${'$'}0, "Device: " ifc) > 0 { print name; exit }
              '
            }

            proxy_state() {
              # $1 = "off" or "restore"
              SVC=`active_service`
              [ -z "${'$'}SVC" ] && return
              if [ "${'$'}1" = "off" ]; then
                networksetup -getwebproxystate "${'$'}SVC" 2>/dev/null | tr -d '\n' > "${'$'}DIR/webproxy.state"
                networksetup -getsecurewebproxystate "${'$'}SVC" 2>/dev/null | tr -d '\n' > "${'$'}DIR/swproxy.state"
                networksetup -setwebproxystate "${'$'}SVC" off >/dev/null 2>&1
                networksetup -setsecurewebproxystate "${'$'}SVC" off >/dev/null 2>&1
              else
                case "${'$'}(cat "${'$'}DIR/webproxy.state" 2>/dev/null)" in *"Enabled: Yes"*) networksetup -setwebproxystate "${'$'}SVC" on >/dev/null 2>&1;; esac
                case "${'$'}(cat "${'$'}DIR/swproxy.state" 2>/dev/null)" in *"Enabled: Yes"*) networksetup -setsecurewebproxystate "${'$'}SVC" on >/dev/null 2>&1;; esac
                rm -f "${'$'}DIR/webproxy.state" "${'$'}DIR/swproxy.state"
              fi
            }

            stop_tun() {
              proxy_state restore
              [ -f "${'$'}DIR/tun.pid" ] && kill `cat "${'$'}DIR/tun.pid"` 2>/dev/null
              rm -f "${'$'}DIR/tun.pid" "${'$'}DIR/utun.name"
              if [ -f "${'$'}DIR/dns_restore" ]; then
                SVC=`active_service`
                if [ -n "${'$'}SVC" ]; then
                  set -- `cat "${'$'}DIR/dns_restore"`
                  if [ "${'$'}1" = "No" ]; then
                    networksetup -setdnsservers "${'$'}SVC" Empty >/dev/null 2>&1
                  else
                    networksetup -setdnsservers "${'$'}SVC" ${'$'}@ >/dev/null 2>&1
                  fi
                fi
                rm -f "${'$'}DIR/dns_restore"
              fi
            }

            handle() {
              case "${'$'}1" in
                start_tunnel)
                  stop_tun
                  sleep 1
                  proxy_state off
                  TOOL=`cat "${'$'}DIR/tun_tool"`
                  CONF=`cat "${'$'}DIR/current_conf"`
                  [ -f "${'$'}DIR/dns_servers" ] || : > "${'$'}DIR/dns_servers"
                  if [ -s "${'$'}DIR/dns_servers" ]; then
                    SVC=`active_service`
                    if [ -n "${'$'}SVC" ]; then
                      networksetup -getdnsservers "${'$'}SVC" > "${'$'}DIR/dns_restore" 2>/dev/null
                      # shellcheck disable=SC2046
                      networksetup -setdnsservers "${'$'}SVC" `cat "${'$'}DIR/dns_servers"` >/dev/null 2>&1
                    fi
                  fi
                  if [ -x "${'$'}TOOL" ] && [ -f "${'$'}CONF" ]; then
                    "${'$'}TOOL" "${'$'}CONF" "${'$'}DIR/utun.name" >> "${'$'}DIR/tun.log" 2>&1 &
                    echo ${'$'}! > "${'$'}DIR/tun.pid"
                  fi
                  ;;
                stop_tunnel)
                  stop_tun
                  ;;
              esac
            }

            uninstall() {
              stop_tun
              rm -f "${'$'}DIR/cmd" "${'$'}DIR/cmd.done" "${'$'}DIR/stop" "${'$'}DIR/watcher.started" "${'$'}DIR/heartbeat"
              launchctl bootout system/$DAEMON_LABEL 2>/dev/null
              rm -f "$DAEMON_PLIST_PATH"
              exit 0
            }

            [ -f "${'$'}DIR/stop" ] && uninstall
            touch "${'$'}DIR/watcher.started"
            while :; do
              [ -f "${'$'}DIR/stop" ] && uninstall
              if [ -f "${'$'}DIR/tun.pid" ] && kill -0 `cat "${'$'}DIR/tun.pid"` 2>/dev/null; then
                if [ ! -f "${'$'}DIR/heartbeat" ] || [ $(( `date +%s` - `stat -f %m "${'$'}DIR/heartbeat"` )) -gt 180 ]; then
                  stop_tun
                fi
              fi
              if [ -f "${'$'}DIR/cmd" ] && [ ! -f "${'$'}DIR/cmd.done" ]; then
                nonce=`head -1 "${'$'}DIR/cmd"`
                action=`sed -n 2p "${'$'}DIR/cmd"`
                handle "${'$'}action"
                echo "${'$'}nonce" > "${'$'}DIR/cmd.done"
              fi
              sleep 0.3
            done
            """.trimIndent() + "\n"
        val previous = scriptFile.takeIf { it.exists() }?.readText()
        if (previous == desired) return false
        scriptFile.writeText(desired)
        return true
    }

    private fun writePlist() {
        plistFile.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
              <key>Label</key><string>$DAEMON_LABEL</string>
              <key>ProgramArguments</key>
              <array><string>/bin/sh</string><string>${scriptFile.absolutePath}</string></array>
              <key>RunAtLoad</key><true/>
              <key>KeepAlive</key><true/>
            </dict>
            </plist>
            """.trimIndent() + "\n",
        )
    }

    private fun startDaemon(): Boolean {
        writeScript()
        writePlist()
        runCatching { stopFile.delete(); startedFile.delete() }
        val installCommand =
            "cp '${plistFile.absolutePath}' '$DAEMON_PLIST_PATH' && " +
                "chown root:wheel '$DAEMON_PLIST_PATH' && chmod 644 '$DAEMON_PLIST_PATH' && " +
                "launchctl bootout system/$DAEMON_LABEL 2>/dev/null; " +
                "launchctl bootstrap system '$DAEMON_PLIST_PATH'"
        val promptScript =
            "do shell script \"$installCommand\" " +
                "with prompt \"TunGate needs administrator access once to manage the VPN tunnel. This is the last time it will ask.\" " +
                "with administrator privileges"
        promptedForAdmin = true
        val result = runCatching {
            ProcessBuilder("osascript", "-e", promptScript)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start().waitFor()
        }
        if (result.getOrDefault(1) != 0) {
            log("Administrator approval declined; the tunnel cannot start.")
            return false
        }
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (startedFile.exists() && watcherAlive()) return true
            Thread.sleep(250)
        }
        return watcherAlive()
    }

    private fun ensureWatcher(): Boolean {
        if (watcherAlive() && !writeScript()) return true
        return startDaemon()
    }

    private fun sendCommand(action: String, timeoutMs: Long = 30_000): Boolean {
        dir.mkdirs()
        if (!ensureWatcher()) return false
        runCatching { doneFile.delete() }
        val nonce = UUID.randomUUID().toString()
        cmdFile.writeText("$nonce\n$action\n")
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (doneFile.exists() && doneFile.readText().trim() == nonce) {
                runCatching { cmdFile.delete(); doneFile.delete() }
                return true
            }
            Thread.sleep(150)
        }
        log("The privileged helper did not answer '$action' in time.")
        return false
    }

    fun startTunnel(confText: String, dns: List<String>): Boolean {
        val bundled = Paths.bundledTool("tungatun")
        if (bundled == null) {
            log("The tunnel engine (tungatun) is missing from the app bundle.")
            return false
        }
        // Run the engine from our own writable directory: bundle copies can lose the
        // executable bit, and jpackage strips it on some layouts.
        val tool = File(dir, "tungatun")
        // Overwriting in place invalidates the ad-hoc code signature and macOS then
        // SIGKILLs the binary; always replace it wholesale and re-sign.
        runCatching {
            if (!tool.exists() || tool.length() != bundled.length() || tool.lastModified() < bundled.lastModified()) {
                tool.delete()
                bundled.copyTo(tool, overwrite = true)
                tool.setExecutable(true, false)
                ProcessBuilder("codesign", "-s", "-", "--force", tool.absolutePath)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start().waitFor()
            }
        }
        val conf = File(dir, "tunnel.conf")
        conf.writeText(confText)
        toolFile.writeText(tool.absolutePath)
        confPathFile.writeText(conf.absolutePath)
        dnsFile.writeText(dns.joinToString(" ") + "\n")
        if (!sendCommand("start_tunnel")) return false
        heartbeat()
        // wait for utun.name (engine up + routes installed)
        val deadline = System.currentTimeMillis() + 12_000
        while (System.currentTimeMillis() < deadline) {
            if (utunNameFile.exists() && utunNameFile.readText().isNotBlank()) return true
            Thread.sleep(250)
        }
        log("The tunnel engine did not come up; check helper/tun.log")
        return false
    }

    fun stopTunnel(): Boolean = sendCommand("stop_tunnel")

    fun heartbeat() {
        runCatching { heartbeatFile.writeText(System.currentTimeMillis().toString()) }
    }

    fun activeInterface(): String = runCatching { utunNameFile.readText().trim() }.getOrDefault("")

    fun releaseOnQuit() {
        runCatching { stopTunnel() }
    }
}
