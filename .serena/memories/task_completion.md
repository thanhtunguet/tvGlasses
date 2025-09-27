# Task Completion Checklist
- Build and test before handing off: `./gradlew lint`, `./gradlew test`, and if UI/device features change consider `./gradlew connectedAndroidTest`.
- For features affecting app behavior, install and smoke-test on target glasses/emulator via `./gradlew installDebug`.
- Update README or in-app resources if user-facing flows or configuration options change.
- Ensure new assets (strings, drawables) follow Android resource naming conventions and are referenced in the manifest or layouts as needed.