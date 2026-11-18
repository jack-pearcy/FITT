// Draws the app icon and writes it as a PNG.
//
//   swift mobile/tools/make-icon.swift mobile/App/Assets.xcassets/AppIcon.appiconset/icon-1024.png
//
// The icon is a barbell seen from the side with four plates, one for each
// letter of FITT, in the two colors of the app's theme. iOS wants one square
// 1024 point image with no transparency; the system rounds the corners.
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

let size = 1024
let side = CGFloat(size)

func color(_ hex: UInt32, _ alpha: CGFloat = 1) -> CGColor {
    CGColor(
        srgbRed: CGFloat((hex >> 16) & 0xFF) / 255,
        green: CGFloat((hex >> 8) & 0xFF) / 255,
        blue: CGFloat(hex & 0xFF) / 255,
        alpha: alpha
    )
}

let space = CGColorSpace(name: CGColorSpace.sRGB)!
// noneSkipLast: four bytes a pixel with the last ignored, so the file has no alpha.
let context = CGContext(
    data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
    space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
)!

func gradient(_ colors: [CGColor], in rect: CGRect, radius: CGFloat = 0) {
    context.saveGState()
    context.addPath(CGPath(roundedRect: rect, cornerWidth: radius, cornerHeight: radius, transform: nil))
    context.clip()
    let fill = CGGradient(colorsSpace: space, colors: colors as CFArray, locations: nil)!
    // Core Graphics has its origin at the bottom, so maxY is the top edge.
    context.drawLinearGradient(
        fill,
        start: CGPoint(x: rect.midX, y: rect.maxY),
        end: CGPoint(x: rect.midX, y: rect.minY),
        options: []
    )
    context.restoreGState()
}

// Background: the card tone at the top falling to the canvas tone.
gradient([color(0x1D212A), color(0x0B0D10)], in: CGRect(x: 0, y: 0, width: side, height: side))

let mid = side / 2

// The bar and its two collars, in the muted steel of the theme's secondary text.
let barHeight: CGFloat = 40
gradient(
    [color(0xC3CAD6), color(0x8A94A6), color(0x5B6474)],
    in: CGRect(x: 112, y: mid - barHeight / 2, width: side - 224, height: barHeight),
    radius: barHeight / 2
)

// Four plates, mirrored about the center: two tall inner ones and two shorter outer ones.
struct Plate {
    let offset: CGFloat   // distance of the plate's center from the middle of the icon
    let width: CGFloat
    let height: CGFloat
}
let plates = [Plate(offset: 214, width: 104, height: 560), Plate(offset: 346, width: 84, height: 400)]

for plate in plates {
    for direction in [CGFloat(-1), 1] {
        let rect = CGRect(
            x: mid + direction * plate.offset - plate.width / 2,
            y: mid - plate.height / 2,
            width: plate.width,
            height: plate.height
        )
        gradient([color(0xDCFF4D), color(0xC8FF00), color(0x9FCC00)], in: rect, radius: 30)
        // A thin line of light on the top edge, as on the app's glass bars.
        gradient(
            [color(0xFFFFFF, 0.55), color(0xFFFFFF, 0)],
            in: CGRect(x: rect.minX + 12, y: rect.maxY - 22, width: rect.width - 24, height: 14),
            radius: 7
        )
    }
}

// Collars sit just inside the inner plates.
for direction in [CGFloat(-1), 1] {
    gradient(
        [color(0xE2E7EF), color(0x8A94A6)],
        in: CGRect(x: mid + direction * 130 - 18, y: mid - 46, width: 36, height: 92),
        radius: 12
    )
}

guard CommandLine.arguments.count == 2 else {
    FileHandle.standardError.write(Data("usage: make-icon.swift <output.png>\n".utf8))
    exit(2)
}
let url = URL(fileURLWithPath: CommandLine.arguments[1])
let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)!
CGImageDestinationAddImage(destination, context.makeImage()!, nil)
guard CGImageDestinationFinalize(destination) else {
    FileHandle.standardError.write(Data("could not write \(url.path)\n".utf8))
    exit(1)
}
