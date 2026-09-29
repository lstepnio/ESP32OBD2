import EGaugeCore
import SwiftUI

struct AlertEditorView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss

    let readingId: String
    @State private var loaded = false
    @State private var direction: AlertDirection = .above
    @State private var warning = ""
    @State private var critical = ""
    @State private var reset = "3"
    @State private var showAfter = "1"
    @State private var clearAfter = "2"
    @State private var priority = 8
    @State private var showBehavior = false
    @State private var showPreview = false
    @State private var preview: GaugePreview.PreviewCondition = .normal

    private var reading: ReadingDefinition? { ReadingDefinition.find(readingId) }
    private var existing: GaugeAlert? { store.active.draft.alerts.first { $0.pidId == readingId } }
    private var candidate: GaugeAlert? {
        guard let base = GaugeAlert.initial(for: readingId),
              let warning = Int(warning), let critical = Int(critical),
              let reset = Int(reset), let showSeconds = Int(showAfter), let clearSeconds = Int(clearAfter),
              (0...60).contains(showSeconds), (0...60).contains(clearSeconds) else { return nil }
        var result = base
        result.id = existing?.id ?? base.id
        result.direction = direction
        result.warning = warning
        result.critical = critical
        result.hysteresis = reset
        result.triggerDwellMs = showSeconds * 1_000
        result.clearDwellMs = clearSeconds * 1_000
        result.priority = priority
        return result
    }
    private var errors: [String] {
        guard let candidate else { return ["Enter whole numbers for every alert field."] }
        return GaugeDraft(alerts: [candidate]).sendBlockers()
    }

    var body: some View {
        Form {
            Section {
                Text(reading?.name ?? "Reading").font(.title2.bold())
                Text("Applies wherever this reading is used.")
                    .font(.subheadline).foregroundStyle(GaugeTheme.muted)
            }
            Section("Alert when the reading") {
                Picker("Direction", selection: $direction) {
                    ForEach(AlertDirection.allCases, id: \.self) { direction in
                        Text(direction.label).tag(direction)
                    }
                }
                .pickerStyle(.segmented)
                numberField("Warning", text: $warning, unit: reading?.unit ?? "")
                numberField("Critical", text: $critical, unit: reading?.unit ?? "")
                if let reading {
                    Text("Supported range: \(reading.range.lowerBound) to \(reading.range.upperBound) \(reading.unit)")
                        .font(.footnote).foregroundStyle(GaugeTheme.muted)
                }
            }
            Section {
                DisclosureGroup("Alert behavior", isExpanded: $showBehavior) {
                    numberField("Show after", text: $showAfter, unit: "s")
                    numberField("Clear after", text: $clearAfter, unit: "s")
                    numberField("Reset distance", text: $reset, unit: reading?.unit ?? "")
                    Stepper("Priority \(priority)", value: $priority, in: 0...15)
                    Text("A value must stay beyond a limit before an alert appears. It must move back by the reset distance before clearing.")
                        .font(.footnote).foregroundStyle(GaugeTheme.muted)
                }
            }
            if !errors.isEmpty {
                Section("Check your alert") {
                    ForEach(errors, id: \.self) { Text($0).foregroundStyle(GaugeTheme.warning) }
                }
            }
            Section {
                Button(showPreview ? "Hide preview" : "Preview alert") { showPreview.toggle() }
                if showPreview, let reading {
                    GaugePreview(page: GaugePage(id: "example", name: reading.name,
                        layout: .Arc, pidIds: [readingId]), condition: preview)
                        .frame(maxWidth: 200)
                        .frame(maxWidth: .infinity)
                    Picker("Preview condition", selection: $preview) {
                        ForEach(GaugePreview.PreviewCondition.allCases) { Text($0.rawValue).tag($0) }
                    }
                    Text("Preview uses example values. Nothing was sent to the gauge.")
                        .font(.footnote).foregroundStyle(GaugeTheme.muted)
                }
            }
            if existing != nil {
                Section {
                    Button("Remove alert", role: .destructive) {
                        store.updateDraft { $0.alerts.removeAll { $0.pidId == readingId } }
                        dismiss()
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .navigationTitle("Edit alert")
        .toolbar {
            ToolbarItem(placement: .topBarLeading) { Button("Cancel") { dismiss() } }
            ToolbarItem(placement: .topBarTrailing) {
                Button("Save") {
                    guard let candidate, errors.isEmpty else { return }
                    store.updateDraft { draft in
                        draft.alerts.removeAll { $0.pidId == readingId }
                        draft.alerts.append(candidate)
                    }
                    dismiss()
                }
                .disabled(!store.canEdit || !errors.isEmpty)
            }
        }
        .onAppear {
            guard !loaded else { return }
            loaded = true
            guard let initial = existing ?? GaugeAlert.initial(for: readingId) else { return }
            direction = initial.direction
            warning = String(initial.warning)
            critical = String(initial.critical)
            reset = String(initial.hysteresis)
            showAfter = String(initial.triggerDwellMs / 1_000)
            clearAfter = String(initial.clearDwellMs / 1_000)
            priority = initial.priority
        }
        .gaugePage()
    }

    private func numberField(_ label: String, text: Binding<String>, unit: String) -> some View {
        HStack {
            Text(label)
            Spacer()
            TextField(label, text: text)
                .multilineTextAlignment(.trailing)
                .keyboardType(.numbersAndPunctuation)
                .accessibilityLabel("\(label) in \(unit)")
            Text(unit).foregroundStyle(GaugeTheme.muted)
        }
    }
}
