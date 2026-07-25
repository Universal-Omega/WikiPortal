import SwiftUI
import ComposeApp

@main
struct iOSApp: App {

    init() {
        // Named `initKoin()` in Kotlin, but Kotlin/Native's Objective-C
        // exporter treats any `init`-prefixed function as an ObjC-style
        // initializer and renames it on export to avoid the collision.
        MainViewControllerKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.keyboard) // Compose handles its own IME insets
        }
    }
}
