import EGaugeCore
import SwiftUI

struct GaugePreview: View {
    let page: GaugePage
    var condition: PreviewCondition = .normal

    enum PreviewCondition: String, CaseIterable, Identifiable {
        case normal = "Normal", warning = "Warning", critical = "Critical", stale = "Stale"
        var id: String { rawValue }
    }

    private var reading: ReadingDefinition? { ReadingDefinition.find(page.pidIds.first ?? "") }
    private var second: ReadingDefinition? { page.pidIds.dropFirst().first.flatMap(ReadingDefinition.find) }
    private var accent: Color {
        switch condition {
        case .warning: DesignTokenColor.warning
        case .critical: DesignTokenColor.critical
        case .stale: DesignTokenColor.muted
        case .normal: DesignTokenColor.accent
        }
    }

    var body: some View {
        GeometryReader { geometry in
            let size = min(geometry.size.width, geometry.size.height)
            ZStack {
                Circle().fill(DesignTokenColor.canvas)
                Circle().stroke(DesignTokenColor.surfaceRaised, lineWidth: size * 0.045)
                    .padding(size * 0.035)
                if page.layout == .Arc {
                    Circle().trim(from: 0, to: 0.66)
                        .stroke(accent, style: StrokeStyle(lineWidth: size * 0.045, lineCap: .round))
                        .rotationEffect(.degrees(140))
                        .padding(size * 0.035)
                }
                VStack(spacing: size * 0.02) {
                    Text(page.name.uppercased())
                        .font(.system(size: size * 0.07, weight: .semibold, design: .rounded))
                        .lineLimit(1).minimumScaleFactor(0.75)
                    if page.layout == .Dual, let second {
                        VStack(spacing: 1) {
                            Text("\(reading?.example ?? "--") \(reading?.unit ?? "")")
                                .font(.system(size: size * 0.15, weight: .bold, design: .rounded))
                                .lineLimit(1).minimumScaleFactor(0.55)
                            Rectangle().fill(DesignTokenColor.surfaceRaised).frame(height: 1)
                            Text("\(second.example) \(second.unit)")
                                .font(.system(size: size * 0.13, weight: .semibold, design: .rounded))
                                .lineLimit(1).minimumScaleFactor(0.55)
                        }
                    } else {
                        Text(reading?.example ?? "--")
                            .font(.system(size: size * 0.22, weight: .bold, design: .rounded))
                            .lineLimit(1).minimumScaleFactor(0.55)
                            .monospacedDigit()
                            .contentTransition(.numericText())
                        Text(reading?.unit ?? "")
                            .font(.system(size: size * 0.09, weight: .medium, design: .rounded))
                    }
                    if page.layout == .Bar {
                        GeometryReader { bar in
                            Capsule().fill(DesignTokenColor.surfaceRaised)
                                .overlay(alignment: .leading) {
                                    Capsule().fill(accent).frame(width: bar.size.width * 0.6)
                                }
                        }.frame(height: max(6, size * 0.035))
                    }
                    if page.layout == .Trend {
                        TrendShape().stroke(accent, style: StrokeStyle(lineWidth: max(2, size * 0.012), lineCap: .round))
                            .frame(height: size * 0.19)
                    }
                    Text("PREVIEW")
                        .font(.system(size: size * 0.055, weight: .bold, design: .rounded))
                        .tracking(1.5).foregroundStyle(DesignTokenColor.muted)
                }
                .frame(width: size * 0.80)
                .foregroundStyle(accent)
            }
            .frame(width: size, height: size)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Example gauge page, \(page.name), \(reading?.example ?? "no value") \(reading?.unit ?? ""), \(page.layout.label) layout")
    }
}

private struct TrendShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let points: [CGFloat] = [0.67, 0.61, 0.65, 0.53, 0.58, 0.48, 0.54, 0.36, 0.43, 0.25, 0.32, 0.23]
        for (index, value) in points.enumerated() {
            let point = CGPoint(x: rect.width * CGFloat(index) / CGFloat(points.count - 1),
                                y: rect.height * value)
            if index == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        return path
    }
}
