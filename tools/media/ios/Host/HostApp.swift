import SwiftUI

/// XCUITest needs a host app. The scenes drive the demo app by its bundle identifier instead.
@main
struct HostApp: App {
    var body: some Scene { WindowGroup { Text("Capsa media driver") } }
}
