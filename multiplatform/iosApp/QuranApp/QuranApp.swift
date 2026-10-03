import SwiftUI
import QuranShared

@main
struct QuranApp: App {
    var body: some Scene {
        WindowGroup { QuranRoot().ignoresSafeArea(.keyboard) }
    }
}
struct QuranRoot: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController { MainViewControllerKt.MainViewController() }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
