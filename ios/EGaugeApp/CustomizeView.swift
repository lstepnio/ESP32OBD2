import EGaugeCore
import SwiftUI

private enum EditorSection { case page, manage, alerts, review }
private enum ChoiceKind: String, Identifiable { case reading, secondReading, layout, addPage
    var id: String { rawValue }
}

struct CustomizeView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @State private var selectedPage: Int
    @State private var section: EditorSection = .page
    @State private var choice: ChoiceKind?
    @State private var alertReading: String?

    init(initialPage: Int = 0) { _selectedPage = State(initialValue: initialPage) }

    private var draft: GaugeDraft { store.active.draft }
    private var current: GaugePage? { draft.pages.indices.contains(selectedPage) ? draft.pages[selectedPage] : draft.pages.first }
    private var currentReading: ReadingDefinition? { current?.pidIds.first.flatMap(ReadingDefinition.find) }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    switch section {
                    case .page: pageContent
                    case .manage: manageContent
                    case .alerts: alertsContent
                    case .review: reviewContent
                    }
                }
                .padding(20)
                .frame(maxWidth: 740)
                .frame(maxWidth: .infinity)
            }
            bottomAction
        }
        .navigationTitle(section == .page ? "Customize" : section == .manage ? "Manage pages" : section == .alerts ? "All alerts" : "Review and send")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                if section != .page { Button("Back") { section = .page } }
            }
        }
        .sheet(item: $choice) { choice in choiceSheet(choice) }
        .sheet(item: Binding(
            get: { alertReading.map(AlertSelection.init) },
            set: { alertReading = $0?.id }
        )) { selection in
            NavigationStack { AlertEditorView(readingId: selection.id) }
        }
        .onChange(of: draft.pages.count) { _, count in
            selectedPage = min(selectedPage, max(0, count - 1))
        }
        .gaugePage()
    }

    private var pageContent: some View {
        VStack(alignment: .leading, spacing: 14) {
            if let current {
                TabView(selection: $selectedPage) {
                    ForEach(draft.pages.indices, id: \.self) { index in
                        VStack(spacing: 8) {
                            GaugePreview(page: draft.pages[index])
                                .frame(maxWidth: 245)
                            Text("Page \(index + 1) of \(draft.pages.count)")
                                .font(.caption).foregroundStyle(GaugeTheme.muted)
                        }
                        .tag(index)
                    }
                }
                .frame(height: 290)
                .tabViewStyle(.page(indexDisplayMode: .always))
                .accessibilityLabel("Gauge pages. Swipe to change page.")
                Text("Preview values are examples. Swipe to change pages.")
                    .font(.footnote).foregroundStyle(GaugeTheme.muted)

                HStack {
                    Button("Add page") { choice = .addPage }
                        .disabled(!store.canEdit || draft.pages.count >= 8)
                    Spacer()
                    Button("Manage pages") { section = .manage }
                }
                .buttonStyle(.bordered)

                GaugeCard {
                    VStack(spacing: 0) {
                        editorRow("Reading", currentReading?.name ?? current.pidIds.first ?? "Unknown") {
                            choice = .reading
                        }
                        Divider()
                        editorRow("Layout", current.layout.label) { choice = .layout }
                        if current.layout == .Dual {
                            Divider()
                            let second = current.pidIds.dropFirst().first.flatMap(ReadingDefinition.find)
                            editorRow("Second reading", second?.name ?? "Choose reading") {
                                choice = .secondReading
                            }
                        }
                    }
                }
                GaugeCard {
                    VStack(spacing: 0) {
                        ForEach(current.pidIds, id: \.self) { id in
                            let reading = ReadingDefinition.find(id)
                            let alert = draft.alerts.first(where: { $0.pidId == id })
                            editorRow("\(reading?.name ?? id) alert", alert.map(alertSummary) ?? "Off") {
                                alertReading = id
                            }
                            if id != current.pidIds.last { Divider() }
                        }
                    }
                }
                Button("All alerts (\(draft.alerts.count))") { section = .alerts }
                    .frame(maxWidth: .infinity)
                if let blocker = draft.sendBlockers().first {
                    GaugeCard { Label(blocker, systemImage: "exclamationmark.triangle")
                        .foregroundStyle(GaugeTheme.warning) }
                }
            } else {
                Text("Add a reading to start your gauge.")
            }
        }
    }

    private func editorRow(_ title: String, _ detail: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(.subheadline).foregroundStyle(GaugeTheme.muted)
                    Text(detail).font(.body).foregroundStyle(GaugeTheme.text)
                }
                Spacer()
                Image(systemName: "chevron.right").foregroundStyle(GaugeTheme.muted)
            }
            .frame(minHeight: 48)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!store.canEdit)
    }

    private var manageContent: some View {
        VStack(alignment: .leading, spacing: 14) {
            Button("Add page") { choice = .addPage }
                .buttonStyle(.bordered)
                .disabled(!store.canEdit || draft.pages.count >= 8)
            Text("Keep at least one page. The order here is the order on the gauge.")
                .font(.footnote).foregroundStyle(GaugeTheme.muted)
            ForEach(draft.pages.indices, id: \.self) { index in
                let page = draft.pages[index]
                GaugeCard {
                    HStack(spacing: 12) {
                        Text("\(index + 1)").font(.title2.bold()).foregroundStyle(GaugeTheme.accent)
                        VStack(alignment: .leading) {
                            Text(ReadingDefinition.find(page.pidIds[0])?.name ?? page.name).font(.headline)
                            Text(page.layout.label).font(.caption).foregroundStyle(GaugeTheme.muted)
                        }
                        Spacer()
                        Menu {
                            Button("Edit page") { selectedPage = index; section = .page }
                            Button("Move earlier") { move(index, -1) }.disabled(index == 0)
                            Button("Move later") { move(index, 1) }.disabled(index == draft.pages.count - 1)
                            Button("Remove page", role: .destructive) { remove(index) }
                                .disabled(draft.pages.count <= 1)
                        } label: {
                            Image(systemName: "ellipsis.circle").frame(minWidth: 44, minHeight: 44)
                        }.disabled(!store.canEdit)
                    }
                }
            }
        }
    }

    private var alertsContent: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Alerts apply anywhere their reading appears.")
                .font(.footnote).foregroundStyle(GaugeTheme.muted)
            ForEach(draft.alerts) { alert in
                GaugeCard {
                    Button {
                        alertReading = alert.pidId
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 5) {
                                Text(ReadingDefinition.find(alert.pidId)?.name ?? alert.pidId).font(.headline)
                                Text(alertSummary(alert)).font(.subheadline).foregroundStyle(GaugeTheme.muted)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            if draft.alerts.isEmpty { Text("No alerts set").foregroundStyle(GaugeTheme.muted) }
        }
    }

    private var reviewContent: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("\(draft.pages.count) pages").font(.title2.bold())
            GaugeCard {
                VStack(alignment: .leading, spacing: 12) {
                    ForEach(Array(draft.pages.enumerated()), id: \.element.id) { index, page in
                        VStack(alignment: .leading, spacing: 3) {
                            Text("\(index + 1). \(page.pidIds.compactMap { ReadingDefinition.find($0)?.name }.joined(separator: " + "))")
                                .font(.headline)
                            Text(page.layout.label).font(.subheadline).foregroundStyle(GaugeTheme.muted)
                        }
                    }
                }
            }
            Text("Alerts").font(.title2.bold())
            GaugeCard {
                if draft.alerts.isEmpty { Text("No alerts set") }
                else { VStack(alignment: .leading, spacing: 10) {
                    ForEach(draft.alerts) { alert in
                        Text("\(ReadingDefinition.find(alert.pidId)?.name ?? alert.pidId): \(alertSummary(alert))")
                    }
                } }
            }
            ForEach(draft.sendBlockers(), id: \.self) { issue in
                GaugeCard { Label(issue, systemImage: "exclamationmark.triangle")
                    .foregroundStyle(GaugeTheme.warning) }
            }
            GaugeCard {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Saved on this phone").font(.headline)
                    Text("Sending to the gauge is not available in this iOS build. Your preview changes remain here.")
                        .foregroundStyle(GaugeTheme.muted)
                    Text("Example values do not confirm what your car supports.")
                        .font(.footnote).foregroundStyle(GaugeTheme.muted)
                }
            }
        }
    }

    private var bottomAction: some View {
        Button {
            if section == .page { section = .review }
            else { section = .page }
        } label: {
            Text(section == .page ? "Review setup" : "Done")
                .font(.headline).frame(maxWidth: .infinity).padding(15)
        }
        .buttonStyle(.plain)
        .foregroundStyle(GaugeTheme.canvas)
        .background(GaugeTheme.accent, in: RoundedRectangle(cornerRadius: 14))
        .padding(16)
        .background(GaugeTheme.canvas)
        .disabled(!store.canEdit)
    }

    @ViewBuilder
    private func choiceSheet(_ kind: ChoiceKind) -> some View {
        NavigationStack {
            List {
                if kind == .layout {
                    ForEach(GaugeLayout.allCases, id: \.self) { layout in
                        Button {
                            setLayout(layout)
                            choice = nil
                        } label: {
                            HStack {
                                Text(layout.label)
                                Spacer()
                                if current?.layout == layout { Image(systemName: "checkmark") }
                            }
                        }
                    }
                } else {
                    ForEach(ReadingDefinition.executable.filter {
                        kind != .secondReading || $0.id != current?.pidIds.first
                    }) { reading in
                        Button {
                            chooseReading(reading.id, for: kind)
                            choice = nil
                        } label: {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(reading.name)
                                    Text("\(reading.unit) · Example only")
                                        .font(.caption).foregroundStyle(GaugeTheme.muted)
                                }
                                Spacer()
                                if kind == .reading && current?.pidIds.first == reading.id {
                                    Image(systemName: "checkmark")
                                }
                            }
                        }
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .navigationTitle(kind == .layout ? "Choose layout" : kind == .addPage ? "Add page" : "Choose reading")
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Close") { choice = nil } } }
            .gaugePage()
        }
        .presentationDetents([.medium, .large])
    }

    private func chooseReading(_ id: String, for kind: ChoiceKind) {
        guard store.canEdit else { return }
        if kind == .addPage {
            guard draft.pages.count < 8 else { return }
            let name = ReadingDefinition.find(id)?.gaugeLabel ?? id
            store.updateDraft { draft in
                let page = GaugePage(id: "page.\(UUID().uuidString.lowercased())", name: name,
                                     layout: .Numeric, pidIds: [id])
                draft.pages.append(page)
            }
            selectedPage = draft.pages.count - 1
            section = .page
        } else {
            guard draft.pages.indices.contains(selectedPage) else { return }
            store.updateDraft { draft in
                if kind == .secondReading, draft.pages[selectedPage].pidIds.count > 1 {
                    draft.pages[selectedPage].pidIds[1] = id
                } else if kind == .reading {
                    draft.pages[selectedPage].pidIds[0] = id
                    draft.pages[selectedPage].name = ReadingDefinition.find(id)?.gaugeLabel ?? id
                    draft.pidId = id
                    if draft.pages[selectedPage].pidIds.count > 1 && draft.pages[selectedPage].pidIds[1] == id {
                        draft.pages[selectedPage].pidIds[1] = ReadingDefinition.executable.first { $0.id != id }!.id
                    }
                }
            }
        }
    }

    private func setLayout(_ layout: GaugeLayout) {
        guard draft.pages.indices.contains(selectedPage) else { return }
        store.updateDraft { draft in
            draft.pages[selectedPage].layout = layout
            draft.layout = layout
            if layout == .Dual && draft.pages[selectedPage].pidIds.count == 1 {
                let first = draft.pages[selectedPage].pidIds[0]
                draft.pages[selectedPage].pidIds.append(ReadingDefinition.executable.first { $0.id != first }!.id)
            } else if layout != .Dual {
                draft.pages[selectedPage].pidIds = Array(draft.pages[selectedPage].pidIds.prefix(1))
            }
        }
    }

    private func move(_ index: Int, _ delta: Int) {
        guard draft.pages.indices.contains(index + delta) else { return }
        store.updateDraft { $0.pages.swapAt(index, index + delta) }
        selectedPage = index + delta
    }

    private func remove(_ index: Int) {
        guard draft.pages.count > 1, draft.pages.indices.contains(index) else { return }
        store.updateDraft { $0.pages.remove(at: index) }
    }

    private func alertSummary(_ alert: GaugeAlert) -> String {
        let unit = ReadingDefinition.find(alert.pidId)?.unit ?? ""
        return "Warn \(alert.direction.rawValue) \(alert.warning) \(unit) · Critical \(alert.direction.rawValue) \(alert.critical) \(unit)"
    }
}

private struct AlertSelection: Identifiable { let id: String }
