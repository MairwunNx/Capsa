import Shared
import SwiftUI

struct ContentView: View {
    // iOS keeps scene storage when it ends the app in the background and drops it when the user closes the app.
    @SceneStorage("compose") private var saved: String?
    @Environment(\.scenePhase) private var phase
    @State private var scene = ComposeScene()

    var body: some View {
        // Compose draws under the bars itself: Scaffold takes the insets.
        ComposeView(scene: scene, restored: saved)
            .ignoresSafeArea()
            .onChange(of: phase) { _, phase in
                if phase != .active, let state = scene.state { saved = state.save() }
            }
    }
}

/// Holds the Kotlin side of the scene, so the view can ask it for the state to keep.
final class ComposeScene {
    var state: SceneState?
}

/// Shows the Compose Multiplatform app from the Shared framework, which Gradle builds before the Swift sources.
struct ComposeView: UIViewControllerRepresentable {
    let scene: ComposeScene
    let restored: String?

    func makeUIViewController(context: Context) -> UIViewController {
        let state = SceneState(restored: restored)
        scene.state = state
        return state.viewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
