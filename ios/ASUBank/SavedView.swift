import SwiftUI

struct SavedView: View {
    @EnvironmentObject private var saved: SavedStore
    @State private var mode = 0

    var body: some View {
        ABStack {
            List {
                Section {
                    Picker("Show", selection: $mode) {
                        Text("Saved \(saved.saved.count)").tag(0)
                        Text("Recent \(saved.recent.count)").tag(1)
                    }
                    .pickerStyle(.segmented)
                    .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
                }
                if mode == 0 {
                    if saved.saved.isEmpty {
                        EmptyState(symbol: "star", title: "No saved files yet", text: "Tap ☆ on any file, or swipe right on it, to keep it here.")
                            .listRowBackground(Color.clear)
                    } else {
                        Section {
                            ForEach(saved.saved) { MaterialRow(m: $0.material) }
                                .onDelete(perform: saved.removeSaved)
                        } footer: { Text("Saved files stay on this device.") }
                    }
                } else {
                    if saved.recent.isEmpty {
                        EmptyState(symbol: "clock", title: "Nothing opened yet", text: "Files you open show up here so you can get back to them.")
                            .listRowBackground(Color.clear)
                    } else {
                        Section {
                            ForEach(saved.recent) { MaterialRow(m: $0.material, meta: $0.date.formatted(.relative(presentation: .named))) }
                                .onDelete(perform: saved.removeRecent)
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle(mode == 0 ? "Saved" : "Recent")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    if mode == 1 && !saved.recent.isEmpty {
                        Button("Clear") { saved.clearRecent() }
                    } else if mode == 0 && !saved.saved.isEmpty {
                        EditButton()
                    }
                }
            }
            .abDestinations()
        }
    }
}
