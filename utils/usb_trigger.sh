#!/system/bin/sh
#
# usb_trigger.sh —— 供电联动：供电自动拉起 App，断电延时关机
#
#   1) 检测到外部供电（充电器 / 电脑 USB / 无线充电）→ 把目标 App 拉到前台；
#   2) 外部供电中断并持续 SHUTDOWN_DELAY 秒 → 关机；
#   3) 倒计时期间供电恢复 → 取消关机；
#   4) 供电状态读不出来时既不累计断电时间，也不关机。
#
# 供电判定（任一成立即算“有电”）：
#   * /sys/class/power_supply/ 下 USB / AC / 无线等节点的 online 或 present 为 1
#   * 电池 status 为 Charging / Full / Not charging
# 断电判定（两条同时成立才算“无电”）：
#   * 所有供电节点都为 0
#   * 电池 status 为 Discharging
# 关机前再用 dumpsys battery 复核一次，避免误关机。
#
# 用法：放进 /data/adb/service.d/ 由 Magisk 开机启动，需要 root 权限。
# 日志：$LOG

PKG="com.example.clock"

BATTERY_STATUS="/sys/class/power_supply/battery/status"

# 断电多少秒后关机
SHUTDOWN_DELAY=60
# 轮询间隔（秒）
POLL_INTERVAL=2
# 拉起 App 后等多久再确认前台
LAUNCH_SETTLE=2

LOG="/data/adb/usb-control.log"

log() {
    echo "$(date '+%F %T') $*" >> "$LOG"
}

# --------------------------------------------------------------- 供电检测 --

# 读取一个 sysfs 节点，去掉空白和换行；读不到时返回空
read_node() {
    [ -r "$1" ] || return 1
    cat "$1" 2>/dev/null | tr -d ' \t\r\n'
}

get_battery_status() {
    read_node "$BATTERY_STATUS"
}

# 开机以来的秒数：用 /proc/uptime，单调递增，不受改系统时间影响
uptime_sec() {
    _t="$(cat /proc/uptime 2>/dev/null)"
    _t="${_t%%.*}"
    _t="${_t##* }"
    echo "${_t:-0}"
}

# 所有可能代表“外部供电”的 power_supply 节点
POWER_NODES=""

discover_power_nodes() {
    _list=""
    for _d in /sys/class/power_supply/*; do
        [ -d "$_d" ] || continue

        _type="$(read_node "$_d/type")"
        _type="$(printf '%s' "$_type" | tr 'a-z' 'A-Z')"

        # 只保留供电类节点：USB / AC / 无线 / DC，跳过 Battery 及其他
        case "$_type" in
            USB*|AC|MAINS|WIRELESS|WIRED|DC) ;;
            *) continue ;;
        esac

        [ -r "$_d/online" ] || [ -r "$_d/present" ] || continue

        _list="$_list $_d"
    done
    POWER_NODES="$_list"
}

# 单个节点：1=有电 0=无电 空=未知
# online 和 present 任一为 1 就算有电，都读到 0 才算无电
read_power_flag() {
    _on="$(read_node "$1/online")"
    _pr="$(read_node "$1/present")"

    if [ "$_on" = "1" ] || [ "$_pr" = "1" ]; then
        echo 1
        return
    fi
    if [ "$_on" = "0" ] || [ "$_pr" = "0" ]; then
        echo 0
        return
    fi
    echo ""
}

# 综合判断：1=有外部供电 0=无外部供电 unknown=无法判断
get_power_state() {
    _present=0
    _absent=0

    for _d in $POWER_NODES; do
        _v="$(read_power_flag "$_d")"
        case "$_v" in
            1) _present=1 ;;
            0) _absent=1 ;;
        esac
    done

    case "$(get_battery_status)" in
        Charging|Full|"Not charging") _present=1 ;;
        Discharging) _absent=1 ;;
    esac

    if [ "$_present" -eq 1 ]; then
        echo 1
    elif [ "$_absent" -eq 1 ]; then
        echo 0
    else
        echo "unknown"
    fi
}

# 打日志用：列出每个供电节点的读数
describe_power_sources() {
    _txt="battery=$(get_battery_status)"
    for _d in $POWER_NODES; do
        _txt="$_txt ${_d##*/}=$(read_power_flag "$_d")"
    done
    echo "$_txt"
}

# 关机前复核：系统电池服务也认为已断电，才返回 0（可以关机）
system_reports_no_power() {
    _out="$(dumpsys battery 2>/dev/null)"
    case "$_out" in
        *"AC powered: true"*|*"USB powered: true"*|\
        *"Wireless powered: true"*|*"Dock powered: true"*) return 1 ;;
    esac
    return 0
}

# --------------------------------------------------------------- App 拉起 --

# 当前前台 App 的包名
get_foreground_pkg() {
    LINE="$(dumpsys activity activities 2>/dev/null \
        | grep -m 1 'topResumedActivity')"

    if [ -z "$LINE" ]; then
        LINE="$(dumpsys activity activities 2>/dev/null \
            | grep -m 1 'mResumedActivity')"
    fi

    if [ -z "$LINE" ]; then
        LINE="$(dumpsys activity activities 2>/dev/null \
            | grep -m 1 'ResumedActivity')"
    fi

    if [ -z "$LINE" ]; then
        LINE="$(dumpsys window windows 2>/dev/null \
            | grep -m 1 'mCurrentFocus')"
    fi

    printf '%s\n' "$LINE" \
        | sed -n 's/.* u[0-9][0-9]* \([^/ ]*\)\/.*/\1/p' \
        | head -n 1
}

handle_power_connected() {
    log "external power detected; checking foreground App"

    if ! pm path "$PKG" >/dev/null 2>&1; then
        log "ERROR: target package not installed: $PKG"
        return
    fi

    FG="$(get_foreground_pkg)"

    if [ "$FG" = "$PKG" ]; then
        log "target already foreground; no action"
        return
    fi

    log "foreground=$FG; launching $PKG"

    monkey -p "$PKG" \
        -c android.intent.category.LAUNCHER 1 >> "$LOG" 2>&1

    sleep "$LAUNCH_SETTLE"

    FG2="$(get_foreground_pkg)"
    log "after launch: foreground=$FG2"

    if [ "$FG2" != "$PKG" ]; then
        log "WARN: target App did not become foreground"
    fi
}

# ------------------------------------------------------------------- 关机 --

request_shutdown() {
    log "external power absent for ${SHUTDOWN_DELAY}s; shutting down"

    sync
    sleep 1

    /system/bin/reboot -p 2>>"$LOG"
    _rc=$?
    log "reboot -p returned rc=$_rc; trying sys.powerctl"

    setprop sys.powerctl shutdown,usb_power_lost
    _rc=$?
    log "sys.powerctl returned rc=$_rc; trying svc power shutdown"

    svc power shutdown 2>>"$LOG"
    _rc=$?
    log "svc power shutdown returned rc=$_rc"

    sleep 8
    log "WARN: shutdown request did not take effect; will retry"
}

# ------------------------------------------------------------------- 主流程 --

# 等 Android 启动完成
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done

sleep 5

# 等电池节点可读（开机早期可能还没生成）
_i=0
while [ -z "$(get_battery_status)" ] && [ "$_i" -lt 30 ]; do
    sleep 2
    _i=$((_i + 1))
done

discover_power_nodes

log "============================================================"
log "usb_trigger started (uid=$(id -u 2>/dev/null))"
log "power nodes:${POWER_NODES:- none}"
log "sources: $(describe_power_sources)"

BAT_SAMPLE="$(get_battery_status)"
if [ -z "$BAT_SAMPLE" ]; then
    log "WARN: cannot read $BATTERY_STATUS; falling back to online nodes only"
fi
if [ -z "$POWER_NODES" ]; then
    log "WARN: no power-supply node with online/present found; shutdown depends on battery status"
fi

# 断电倒计时起点（/proc/uptime 秒），空表示当前没有在倒计时
OFFLINE_SINCE=""
# 上一次的供电状态，用于只在变化时打日志
LAST_STATE=""
# 本轮“有电”是否已经拉过 App
LAUNCHED=0

while true; do
    STATE="$(get_power_state)"

    if [ "$STATE" != "$LAST_STATE" ]; then
        log "power state=$STATE sources: $(describe_power_sources)"
        LAST_STATE="$STATE"
    fi

    case "$STATE" in
        1)
            # 有电：清掉倒计时；每次重新接电只拉一次 App
            OFFLINE_SINCE=""
            if [ "$LAUNCHED" -eq 0 ]; then
                LAUNCHED=1
                handle_power_connected
            fi
            ;;

        0)
            # 没电：断电边沿开始计时
            LAUNCHED=0
            if [ -z "$OFFLINE_SINCE" ]; then
                OFFLINE_SINCE="$(uptime_sec)"
                log "external power lost; shutdown countdown started (${SHUTDOWN_DELAY}s)"
            fi

            NOW="$(uptime_sec)"
            if [ $((NOW - OFFLINE_SINCE)) -ge "$SHUTDOWN_DELAY" ]; then
                # 关机前再确认一次
                CONFIRM="$(get_power_state)"
                case "$CONFIRM" in
                    1)
                        OFFLINE_SINCE=""
                        log "power restored; shutdown cancelled"
                        ;;
                    0)
                        if system_reports_no_power; then
                            OFFLINE_SINCE=""
                            request_shutdown
                            # 只有关机没生效才会走到这里，重新计时，稍后再试
                            OFFLINE_SINCE="$(uptime_sec)"
                        else
                            OFFLINE_SINCE=""
                            log "battery service still reports external power; shutdown cancelled"
                        fi
                        ;;
                    *)
                        log "cannot confirm power loss yet; keep waiting"
                        ;;
                esac
            fi
            ;;

        *)
            # 状态未知：不清除也不重新开始倒计时，更不关机
            ;;
    esac

    sleep "$POLL_INTERVAL"
done
