import XCTest

/// Taps through More again and again for several minutes, the way the "row greys but won't open" bug shows up.
/// Prints NAVTEST lines; the workflow ios-nav-test.yml reads them.
final class NavigationTests: XCTestCase {
    private let rows = ["Theme", "Customize tab bar", "Language", "Saved", "Friends", "My lectures", "Life hacks",
                        "Subjects directory", "Latest uploads", "Contact us", "Community rules"]

    private func launch() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-ab.terms.accepted.v1", "YES", "-startTab", "more", "-noSplash", "YES"]
        if ProcessInfo.processInfo.environment["NAV_STACK"] == "1" { app.launchArguments += ["-ab.nav.stack", "YES"] }
        app.launch()
        return app
    }

    /// Tapping the More tab goes back to its first screen when something else is open
    private func open(_ name: String, in app: XCUIApplication) -> Bool? {
        let row = app.collectionViews.firstMatch.descendants(matching: .any)[name].firstMatch
        var tries = 0
        while !(row.exists && row.isHittable) && tries < 14 {
            app.swipeUp()
            tries += 1
        }
        if !(row.exists && row.isHittable) {
            tries = 0
            while !(row.exists && row.isHittable) && tries < 14 { app.swipeDown(); tries += 1 }
        }
        guard row.exists, row.isHittable else { return nil }
        row.tap()
        let back = app.navigationBars.buttons["More"].firstMatch
        return back.waitForExistence(timeout: 6)
    }

    func testRowsKeepOpening() {
        let app = launch()
        let more = app.tabBars.buttons["More"]
        XCTAssertTrue(more.waitForExistence(timeout: 30))
        let end = Date().addingTimeInterval(6 * 60)
        var opened = 0, failed = 0, round = 0
        while Date() < end {
            round += 1
            for name in rows where Date() < end {
                switch open(name, in: app) {
                case .some(true):
                    opened += 1
                    app.navigationBars.buttons["More"].firstMatch.tap()
                case .some(false):
                    failed += 1
                    print("NAVTEST FAIL round \(round) row \(name) after \(opened) opened")
                    let shot = XCTAttachment(screenshot: app.screenshot())
                    shot.name = "fail-\(failed)-\(name)"
                    shot.lifetime = .keepAlways
                    add(shot)
                case .none:
                    print("NAVTEST NOTFOUND \(name)")
                }
            }
            // moving between tabs and waiting, as a person does
            for tab in ["Home", "Search", "More"] {
                let b = app.tabBars.buttons[tab]
                if b.exists { b.tap() }
                sleep(1)
            }
        }
        print("NAVTEST RESULT rounds \(round) opened \(opened) failed \(failed)")
        XCTAssertEqual(failed, 0)
        XCTAssertGreaterThan(opened, 20)
    }

    func testAppIconChanges() {
        let app = launch()
        XCTAssertTrue(app.tabBars.buttons["More"].waitForExistence(timeout: 30))
        XCTAssertEqual(open("App icon", in: app), true)
        let choice = app.buttons["Graduation cap"].firstMatch
        XCTAssertTrue(choice.waitForExistence(timeout: 5))
        choice.tap()
        let refused = app.alerts["Couldn't change the icon"]
        let system = XCUIApplication(bundleIdentifier: "com.apple.springboard").alerts.firstMatch
        let end = Date().addingTimeInterval(10)
        var outcome = "NOTHING"
        while Date() < end {
            if refused.exists {
                outcome = "REFUSED " + refused.staticTexts.allElementsBoundByIndex.map(\.label).joined(separator: " | ")
                break
            }
            if system.exists { outcome = "CHANGED " + system.label; break }
            if app.alerts.firstMatch.exists { outcome = "CHANGED " + app.alerts.firstMatch.label; break }
            sleep(1)
        }
        print("NAVTEST ICON \(outcome)")
        XCTAssertFalse(outcome.hasPrefix("REFUSED"))
    }
}
