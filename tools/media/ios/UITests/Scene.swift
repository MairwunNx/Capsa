import XCTest

/// Talks to ios.py through files in DRIVER_DIR: writes "ready" once the sample is open, waits for "go" while the
/// recording starts, then writes the taps, "ok" if the scene went as planned, and "done".
class Scene: XCTestCase {
    let app = XCUIApplication(bundleIdentifier: "com.mairwunnx.capsa.samples")
    var taps: [[String: Double]] = []
    var start = Date()
    var ok = true

    var dir: String { ProcessInfo.processInfo.environment["DRIVER_DIR"]! }

    override func setUp() { continueAfterFailure = false }

    func put(_ name: String, _ text: String = "") { try? text.write(toFile: dir + "/" + name, atomically: true, encoding: .utf8) }

    func waitGo() {
        put("width", String(Double(app.windows.firstMatch.frame.width)))
        put("ready")
        while !FileManager.default.fileExists(atPath: dir + "/go") { usleep(50_000) }
        start = Date()
    }

    func finish() {
        let data = try! JSONSerialization.data(withJSONObject: taps)
        put("taps.json", String(data: data, encoding: .utf8)!)
        if ok { put("ok") }
        put("done")
    }

    /// Any element by its label, value, or placeholder. Slow, so scenes look elements up before waitGo.
    func element(_ label: String) -> XCUIElement {
        let match = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@ OR value == %@ OR placeholderValue == %@", label, label, label)).firstMatch
        XCTAssertTrue(match.waitForExistence(timeout: 10), "no element " + label)
        return match
    }

    func point(_ element: XCUIElement) -> CGPoint { CGPoint(x: element.frame.midX, y: element.frame.midY) }

    func at(_ label: String) -> CGPoint { point(element(label)) }

    func tap(_ point: CGPoint) {
        taps.append(["t": Date().timeIntervalSince(start), "x": Double(point.x), "y": Double(point.y), "ms": 120])
        app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: point.x, dy: point.y)).tap()
    }

    func pause(_ seconds: Double) { usleep(useconds_t(seconds * 1_000_000)) }

    /// Starts the app afresh and opens a sample from its home screen.
    func open(_ title: String) {
        app.launch()
        let card = app.staticTexts[title]
        if !card.waitForExistence(timeout: 3) || !card.isHittable {
            app.swipeUp()
            pause(3) // a tap during the fling would only stop it
        }
        for _ in 0..<3 {
            app.staticTexts[title].tap()
            if app.buttons["Back"].waitForExistence(timeout: 3) { break }
        }
        pause(1)
    }
}
