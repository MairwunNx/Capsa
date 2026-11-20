import XCTest

/// One test per example of docs/media: a single action and its result, 5 to 12 seconds.
final class Scenes: Scene {
    func testCounter() {
        open("Counter")
        let plus = at("+1"), minus = at("−1"), reset = at("Reset")
        waitGo()
        for p in Array(repeating: plus, count: 5) + [minus, minus] {
            tap(p)
            pause(0.55)
        }
        tap(reset)
        pause(1.5)
        finish()
    }

    func testSearch() {
        open("Search")
        let field = at("A fruit")
        waitGo()
        tap(field)
        pause(0.9)
        for letter in "berry" {
            app.typeText(String(letter))
            pause(0.2)
        }
        ok = app.staticTexts["Blueberry"].waitForExistence(timeout: 5)
        pause(1.5)
        finish()
    }

    func testLike() {
        open("Like")
        let heart = point(app.buttons["Like"]), online = point(app.switches.firstMatch)
        waitGo()
        tap(online)
        pause(1)
        tap(heart)
        ok = app.staticTexts["Couldn’t save the like"].waitForExistence(timeout: 12)
        pause(2.2)
        finish()
    }

    func testClock() {
        open("Clock")
        pause(1)
        waitGo()
        pause(3)
        XCUIDevice.shared.press(.home)
        pause(3)
        let icon = XCUIApplication(bundleIdentifier: "com.apple.springboard").icons
            .matching(NSPredicate(format: "label CONTAINS[c] 'capsa'")).firstMatch
        if icon.exists && icon.isHittable {
            taps.append(["t": Date().timeIntervalSince(start), "x": Double(icon.frame.midX), "y": Double(icon.frame.midY), "ms": 120])
            icon.tap()
        } else {
            app.activate()
        }
        pause(4)
        finish()
    }

    func testProfile() {
        // iOS gives no saved state to an app it has ended, so this scene saves the form instead of killing the app.
        open("Profile form")
        let name = at("Name"), email = at("Email"), save = at("Save")
        waitGo()
        tap(name)
        pause(0.5)
        app.typeText("Alex")
        pause(0.5)
        tap(email)
        pause(0.5)
        app.typeText("alex@example.com")
        pause(0.7)
        tap(save)
        ok = app.staticTexts["Profile saved"].waitForExistence(timeout: 5)
        pause(1.5)
        finish()
    }

    func testDashboard() {
        open("Dashboard")
        let refresh = at("Refresh both")
        pause(2)
        waitGo()
        for _ in 0..<3 {
            tap(refresh)
            pause(2.6)
        }
        ok = !app.staticTexts["Couldn’t load"].exists
        finish()
    }

    func testCrashFree() {
        open("Crash-free")
        let button = at("Tap"), bug = at("Throw in decide")
        waitGo()
        for (p, seconds) in [(button, 0.6), (button, 0.8), (bug, 1.8), (button, 0.6), (button, 1.5)] {
            tap(p)
            pause(seconds)
        }
        finish()
    }

    func testTwoScreens() {
        open("Two screens")
        let note = at("Buy oat milk"), back = point(app.buttons["Back"])
        waitGo()
        tap(note)
        pause(1.3)
        let star = app.buttons["Star it"]
        _ = star.waitForExistence(timeout: 5)
        tap(point(star))
        ok = app.buttons["Remove the star"].waitForExistence(timeout: 5)
        pause(0.8)
        tap(back)
        pause(2)
        finish()
    }
}
