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
        tapIcon()
        pause(4)
        finish()
    }

    func testProfile() {
        open("Profile form")
        let name = at("Name"), email = at("Email")
        waitGo()
        tap(name)
        pause(0.5)
        app.typeText("Alex")
        pause(0.5)
        tap(email)
        pause(0.5)
        app.typeText("alex@example.com")
        pause(0.9)
        XCUIDevice.shared.press(.home)
        pause(1.5)
        // What iOS does to an app in the background when it needs memory. Closing it in the switcher would drop the state.
        askHost("terminate")
        pause(1)
        tapIcon()
        ok = app.descendants(matching: .any).matching(NSPredicate(format: "value == %@", "alex@example.com")).firstMatch
            .waitForExistence(timeout: 5)
        pause(1)
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
