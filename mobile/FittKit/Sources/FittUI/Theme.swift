import SwiftUI

/// The look of the app in one place: a dark theme built from three surface
/// tones, two text tones, and one accent. Screens take colors from here and
/// never write a hex value of their own.
enum Theme {
    /// The background behind everything.
    static let canvas = Color(hex: 0x0B0D10)
    /// A card: a row in a list or a form.
    static let card = Color(hex: 0x14171D)
    /// A surface nested inside a card, such as a pill for one logged set.
    static let well = Color(hex: 0x1D212A)

    static let textPrimary = Color(hex: 0xF4F6F9)
    static let textSecondary = Color(hex: 0x8A94A6)

    /// The one accent. Kept for active states and the main action of a screen.
    static let accent = Color(hex: 0xC8FF00)

    /// On target, or done.
    static let positive = Color(hex: 0x10B981)
    /// An error, a destructive action, or over target.
    static let negative = Color(hex: 0xEF4444)

    /// Dark glass for a bar that floats over content, and the light on its top edge.
    static let glass = Color(hex: 0x14171D, opacity: 0.72)
    static let specular = Color.white.opacity(0.14)

    /// The spring for every animated change: quick, and it settles without a bounce.
    static let spring = Animation.spring(response: 0.32, dampingFraction: 1)

    static let rowRadius: CGFloat = 14
}

extension Color {
    /// A color from a hex literal such as `0x14171D`.
    init(hex: UInt32, opacity: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }
}

/// A `List` on the canvas, with its rows on the card surface.
struct ThemedList<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        List {
            // A Group hands the modifier to each child, and a section hands it to its rows.
            Group { content }
                .listRowBackground(Theme.card)
        }
        .themedScroll()
    }
}

/// A `Form` on the canvas, with its rows on the card surface.
struct ThemedForm<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        Form {
            Group { content }
                .listRowBackground(Theme.card)
        }
        .themedScroll()
    }
}

/// A section whose header is a small uppercase label.
struct LabeledSection<Content: View>: View {
    private let title: String
    private let content: Content

    init(_ title: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }

    var body: some View {
        Section {
            content
        } header: {
            Text(title).sectionLabel()
        }
    }
}

extension View {
    /// The theme for the whole app. Applied once, at the root.
    func fittTheme() -> some View {
        self
            .tint(Theme.accent)
            .canvas()
            .preferredColorScheme(.dark)
    }

    /// Fills the space a screen has and paints the canvas behind it, so a screen
    /// that is not a list (an empty state, a spinner) is not left on system black.
    func canvas() -> some View {
        self
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Theme.canvas.ignoresSafeArea())
    }

    /// Swaps the system background of a scrolling container for the canvas.
    func themedScroll() -> some View {
        self
            .scrollContentBackground(.hidden)
            .background(Theme.canvas.ignoresSafeArea())
    }

    /// A secondary label: small, uppercase, widely tracked, in the muted text tone.
    func sectionLabel() -> some View {
        self
            .font(.caption2.weight(.semibold))
            .tracking(0.9)
            .textCase(.uppercase)
            .foregroundStyle(Theme.textSecondary)
    }

    /// A live number: a weight, a count, a timer. Digits share one width so a
    /// column of them lines up and a changing value does not shift.
    func metric() -> some View {
        self.monospacedDigit()
    }

    /// The one number a screen is about.
    func heroMetric() -> some View {
        self
            .font(.system(size: 44, weight: .bold, design: .rounded))
            .monospacedDigit()
            .tracking(-1.3)
            .minimumScaleFactor(0.6)
            .lineLimit(1)
    }

    /// A row drawn as its own tile on the nested surface.
    func tile() -> some View {
        self
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(Theme.well, in: RoundedRectangle(cornerRadius: Theme.rowRadius, style: .continuous))
    }

    /// One row of a log, such as a set: a tile of its own, with no divider line.
    func setTile() -> some View {
        self
            .tile()
            .listRowSeparator(.hidden)
            .listRowInsets(EdgeInsets(top: 4, leading: 12, bottom: 4, trailing: 12))
    }

    /// Dark glass with a line of light on its top edge, for a bar that floats over content.
    func glassBar() -> some View {
        self
            .background(Theme.glass)
            .background(.ultraThinMaterial)
            .overlay(alignment: .top) {
                Rectangle().fill(Theme.specular).frame(height: 1)
            }
    }
}
