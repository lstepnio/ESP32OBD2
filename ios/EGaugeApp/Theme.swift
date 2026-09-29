import SwiftUI

enum GaugeTheme {
    private static func adaptive(dark: UIColor, light: UIColor) -> Color {
        Color(uiColor: UIColor { traits in
            traits.userInterfaceStyle == .dark ? dark : light
        })
    }

    static let canvas = adaptive(dark: UIColor(DesignTokenColor.canvas), light: UIColor(DesignTokenColor.paper))
    static let surface = adaptive(dark: UIColor(DesignTokenColor.surface), light: .white)
    static let raised = adaptive(dark: UIColor(DesignTokenColor.surfaceRaised), light: UIColor(DesignTokenColor.paper))
    static let text = adaptive(dark: UIColor(DesignTokenColor.text), light: UIColor(DesignTokenColor.ink))
    static let muted = adaptive(dark: UIColor(DesignTokenColor.muted), light: .secondaryLabel)
    static let accent = adaptive(dark: UIColor(DesignTokenColor.accent),
                                 light: UIColor(red: 0.20, green: 0.39, blue: 0.05, alpha: 1))
    static let warning = DesignTokenColor.warning
    static let critical = DesignTokenColor.critical
}

struct GaugeCard<Content: View>: View {
    @ViewBuilder let content: Content
    var body: some View {
        content
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(18)
            .background(GaugeTheme.surface, in: RoundedRectangle(cornerRadius: 20))
    }
}

struct GaugePageBackground: ViewModifier {
    func body(content: Content) -> some View {
        content
            .background(GaugeTheme.canvas.ignoresSafeArea())
            .foregroundStyle(GaugeTheme.text)
            .tint(GaugeTheme.accent)
    }
}

extension View {
    func gaugePage() -> some View { modifier(GaugePageBackground()) }
}
