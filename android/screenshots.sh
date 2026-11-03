#!/usr/bin/env bash
# Runs on the CI emulator: installs the APK and saves a screenshot of each main screen into shots/.
set -x
APK="$1"
mkdir -p shots
adb install -r "$APK" || exit 1
shot() { # name, extra adb args
  local name="$1"; shift
  adb shell am force-stop org.asubank.app
  adb shell am start -n org.asubank.app/.MainActivity --ez acceptTerms true "$@"
  sleep 14
  adb exec-out screencap -p > "shots/$name.png"
}
shot 1-home --es startTab home
shot 2-search --es startTab search --es startQuery physics
shot 3-saved --es startTab saved
shot 4-upload --es startTab upload
shot 5-more --es startTab more
shot 6-home-dark --es startTab home --ei appearance 2
shot 8-halls --es startTab home --ei appearance 0 --es startPath /guides/halls
shot 9-ratings-intro --es startPath /requests/portal_to_rating
sleep 6
shot 10-ratings --ez rateIntro true --es startPath /requests/portal_to_rating
sleep 10
adb exec-out screencap -p > shots/11-ratings-later.png
shot 12-matcher --ez matcherIntro true --es startPath /requests/matcher_confirm
shot 13-request --es startPath /requests/student_request_portal
shot 14-grading --es startPath /guides/grading-scale-asu
shot 15-groups --es startPath /guides/groups
shot 16-thanks --es startPath /guides/thankslist
shot 17-search-guide --es startPath /guides/tutorial
shot 18-ads --es startPath /requests/student_ads
shot 19-latest --es startPath /guides/latest
sleep 10
adb exec-out screencap -p > shots/19-latest-later.png
adb shell pm clear org.asubank.app
adb shell am start -n org.asubank.app/.MainActivity
sleep 10
adb exec-out screencap -p > shots/7-terms.png
adb logcat -d -s AndroidRuntime:E > shots/crash-log.txt
ls -la shots
