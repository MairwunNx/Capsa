import Shared
import SwiftUI

struct ContentView: View {
    var body: some View {
        // Compose draws under the bars itself: Scaffold takes the insets.
        ComposeView().ignoresSafeArea()
    }
}

/// Shows the Compose Multiplatform app from the Shared framework, which Gradle builds before the Swift sources.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.mainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
