# License

The `MyTermux` project (extended from `termux/termux-app`) is released under the [GNU General Public License v3.0 (GPL-3.0)](https://www.gnu.org/licenses/gpl-3.0.html). See the complete text in [LICENSE](LICENSE).

### Components & Exceptions

- **MyTermux Extensions**:
  - Embedded Android WebView integration & Drawer Session Controller: Released under GPLv3.
  - Native Socket Bridge (`termux-socket-bridge`): C bridge forwarding Unix domain sockets to TCP loopback (GPLv3).
  - Web CLI dispatcher (`termux-web`): Shell CLI tool for session dispatching and CDP port forwarding (GPLv3).
  - Lucide vector assets: Derived from Lucide Icons ([ISC License](https://lucide.dev/license)).
- **Terminal Emulator**:
  - [Terminal Emulator for Android](https://github.com/jackpal/Android-Terminal-Emulator) code is released under the [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) license. See [`terminal-view`](terminal-view) and [`terminal-emulator`](terminal-emulator).
- **Termux Shared**:
  - Check [`termux-shared/LICENSE.md`](termux-shared/LICENSE.md) for `termux-shared` library exceptions (MIT with specific GPLv3 and Apache 2.0 sub-components).
