#!/bin/bash
# 自测：用假的 /sys/class/power_supply 与模拟时钟，验证 usb_trigger.sh 的判定逻辑
# 用法：bash utils/.selftest_usb_trigger.sh utils/usb_trigger.sh
set -u

SRC="${1:?usage: selftest.sh <path-to-usb_trigger.sh>}"
ROOT="$(mktemp -d)"
PASS=0
FAIL=0

ok()  { PASS=$((PASS + 1)); echo "PASS  $*"; }
bad() { FAIL=$((FAIL + 1)); echo "FAIL  $*"; }

build_and_run() {
    local name="$1" init="$2" tl="$3" limit="$4"
    local W="$ROOT/$name" GEN="$ROOT/$name/run.sh"
    mkdir -p "$W/supply"

    {
        echo '#!/bin/bash'
        printf 'W=%q\n' "$W"
        printf 'LIMIT=%q\n' "$limit"
        cat <<'PRE'
clk() { cat "$W/uptime"; }
setclk() { printf '%s' "$1" > "$W/uptime"; }
mk_node() { mkdir -p "$W/supply/$1"; printf '%s\n' "$2" > "$W/supply/$1/type"; }
plug() { printf '%s\n' "$2" > "$W/supply/$1/online"; }
batt() { printf '%s\n' "$1" > "$W/supply/battery/status"; }
ev() { echo "$1 $(clk)" >> "$W/events"; }

getprop() { echo 1; }
pm() { return 0; }
monkey() { printf '%s' 'com.example.clock' > "$W/fg"; ev LAUNCH; }
dumpsys() {
    case "$1" in
        battery)
            echo "Current Battery Service state:"
            echo "  AC powered: false"
            echo "  USB powered: ${DUMPSYS_USB:-false}"
            ;;
        *) echo "  topResumedActivity=u0 $(cat "$W/fg")/.MainActivity u0" ;;
    esac
}
setprop() { ev "SETPROP"; }
svc() { ev "SVC"; }
reboot() { ev REBOOT; exit 0; }
sync() { :; }
sleep() {
    local s="${1:-1}"
    setclk "$(( $(clk) + s ))"
    timeline
    if [ "$(clk)" -gt "$LIMIT" ]; then ev TIMEOUT; exit 0; fi
}
PRE
        echo 'setclk 1000'
        echo "printf '%s' 'com.android.launcher3' > \"\$W/fg\""
        echo ': > "$W/events"'
        echo "$init"
        echo 'timeline() {'
        echo "$tl"
        echo '}'
        sed -e "s#/sys/class/power_supply#$W/supply#g" \
            -e "s#/data/adb/usb-control.log#$W/log#g" \
            -e "s#/proc/uptime#$W/uptime#g" \
            -e "s#/system/bin/reboot#reboot#g" \
            "$SRC"
    } > "$GEN"

    bash "$GEN" > "$W/stdout" 2>&1
    echo "$W"
}

ev_time()  { awk -v p="$2" '$1==p {print $2; exit}' "$1/events" 2>/dev/null; }
ev_count() { awk -v p="$2" '$1==p' "$1/events" 2>/dev/null | wc -l | tr -d ' '; }

in_range() { [ "$1" -ge "$2" ] && [ "$1" -le "$3" ]; }

echo "==================== 1. 充电器：上电拉起 App，拔电 60s 关机"
W="$(build_and_run charger \
    'mk_node usb USB; mk_node pc_port USB; mk_node battery Battery
     plug usb 1; plug pc_port 0; batt Charging' \
    'if [ "$(clk)" -ge 1050 ] && [ ! -f "$W/unplugged" ]; then
         : > "$W/unplugged"; plug usb 0; batt Discharging; ev UNPLUG
     fi' 1200)"
T_UP="$(ev_time "$W" UNPLUG)"; T_REB="$(ev_time "$W" REBOOT)"; N_LAUNCH="$(ev_count "$W" LAUNCH)"
[ "$N_LAUNCH" = "1" ] && in_range "$(ev_time "$W" LAUNCH)" 1000 1010 \
    && ok "上电后拉起了一次 App（t=$(ev_time "$W" LAUNCH)）" \
    || bad "上电后 App 拉起次数/时机不对：count=$N_LAUNCH，事件=$(tr '\n' ' ' < "$W/events")"
if [ -n "$T_REB" ] && [ -n "$T_UP" ] && in_range "$((T_REB - T_UP))" 58 66; then
    ok "拔电 ${T_UP} → 关机 ${T_REB}，间隔 $((T_REB - T_UP))s"
else
    bad "拔电后关机时机不对：unplug=${T_UP:-none} reboot=${T_REB:-none}，事件=$(tr '\n' ' ' < "$W/events")"
fi

echo "==================== 2. 电脑 USB：pc_port=1 也算有电，拔掉也关机"
W="$(build_and_run pc \
    'mk_node usb USB; mk_node pc_port USB; mk_node battery Battery
     plug usb 0; plug pc_port 1; batt Charging' \
    'if [ "$(clk)" -ge 1050 ] && [ ! -f "$W/unplugged" ]; then
         : > "$W/unplugged"; plug pc_port 0; batt Discharging; ev UNPLUG
     fi' 1200)"
T_UP="$(ev_time "$W" UNPLUG)"; T_REB="$(ev_time "$W" REBOOT)"; N_LAUNCH="$(ev_count "$W" LAUNCH)"
[ "$N_LAUNCH" = "1" ] && ok "pc_port 供电也拉起了 App（t=$(ev_time "$W" LAUNCH)）" \
    || bad "pc_port 供电未拉起 App：count=$N_LAUNCH"
if [ -n "$T_REB" ] && [ -n "$T_UP" ] && in_range "$((T_REB - T_UP))" 58 66; then
    ok "拔电 ${T_UP} → 关机 ${T_REB}，间隔 $((T_REB - T_UP))s"
else
    bad "拔电后关机时机不对：unplug=${T_UP:-none} reboot=${T_REB:-none}，事件=$(tr '\n' ' ' < "$W/events")"
fi

echo "==================== 3. 一直插着：不关机、不重复拉起"
W="$(build_and_run stay \
    'mk_node usb USB; mk_node pc_port USB; mk_node battery Battery
     plug usb 1; plug pc_port 0; batt Charging' ':' 1400)"
[ "$(ev_count "$W" REBOOT)" = "0" ] && ok "全程有电，没有关机" \
    || bad "有电却关机了：$(tr '\n' ' ' < "$W/events")"
[ "$(ev_count "$W" LAUNCH)" = "1" ] && ok "App 只拉起一次" \
    || bad "App 被重复拉起：$(tr '\n' ' ' < "$W/events")"

echo "==================== 4. 倒计时中重新插电：取消关机"
W="$(build_and_run replug \
    'mk_node usb USB; mk_node pc_port USB; mk_node battery Battery
     plug usb 1; plug pc_port 0; batt Charging' \
    'if [ "$(clk)" -ge 1050 ] && [ ! -f "$W/u1" ]; then
         : > "$W/u1"; plug usb 0; batt Discharging; ev UNPLUG
     fi
     if [ "$(clk)" -ge 1090 ] && [ ! -f "$W/u2" ]; then
         : > "$W/u2"; plug usb 1; batt Charging; ev REPLUG
     fi' 1400)"
[ "$(ev_count "$W" REBOOT)" = "0" ] && ok "重新插电后没有关机" \
    || bad "重新插电仍然关机：$(tr '\n' ' ' < "$W/events")"
[ "$(ev_count "$W" LAUNCH)" = "2" ] && ok "重新插电后再次拉起 App" \
    || bad "重新插电后 App 拉起次数不对：$(tr '\n' ' ' < "$W/events")"

echo "==================== 5. dumpsys 仍报供电：不关机"
export DUMPSYS_USB=true
W="$(build_and_run veto \
    'mk_node usb USB; mk_node battery Battery
     plug usb 1; batt Charging' \
    'if [ "$(clk)" -ge 1050 ] && [ ! -f "$W/u1" ]; then
         : > "$W/u1"; plug usb 0; batt Discharging; ev UNPLUG
     fi' 1400)"
[ "$(ev_count "$W" REBOOT)" = "0" ] && ok "dumpsys 报有电时不关机" \
    || bad "dumpsys 报有电却关机了：$(tr '\n' ' ' < "$W/events")"
unset DUMPSYS_USB

echo "==================== 6. 读不到电池与供电节点：不关机"
W="$(build_and_run unknown \
    'mk_node battery Battery' ':' 1400)"
[ "$(ev_count "$W" REBOOT)" = "0" ] && [ "$(ev_count "$W" LAUNCH)" = "0" ] \
    && ok "状态未知时既不拉起也不关机" \
    || bad "状态未知却动作了：$(tr '\n' ' ' < "$W/events")"

echo "==================== 7. 全程电池供电（从未接电）：60s 后关机"
W="$(build_and_run battery_only \
    'mk_node battery Battery; mk_node usb USB; plug usb 0; batt Discharging' ':' 1200)"
if [ "$(ev_count "$W" REBOOT)" = "1" ]; then
    ok "电池供电 60s 后关机（t=$(ev_time "$W" REBOOT)）"
else
    bad "电池供电未按时关机：$(tr '\n' ' ' < "$W/events")"
fi
[ "$(ev_count "$W" LAUNCH)" = "0" ] && ok "从未接电，不拉起 App" \
    || bad "从未接电却拉起 App"

echo
echo "==================== 结果：PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ] || { echo "失败现场保存在 $ROOT"; exit 1; }
rm -rf "$ROOT"
