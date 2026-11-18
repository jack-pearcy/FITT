// Draws the app icon and writes it as a PNG.
//
//   swift mobile/tools/make-icon.swift mobile/App/Assets.xcassets/AppIcon.appiconset/icon-1024.png
//
// The icon is a dark figure with both arms raised, on the app's accent color.
// The figure is an original drawing built from the shapes below. It is not
// traced from a photograph and does not depict a particular person.
// iOS wants one square 1024 point image with no transparency; the system
// rounds the corners.
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
// Core Graphics starts at the bottom left. Flip it so y grows downward and the
// numbers below read like a drawing on a page.
context.translateBy(x: 0, y: side)
context.scaleBy(x: 1, y: -1)

// Background: the accent, a little lighter at the top.
let background = CGGradient(
    colorsSpace: space, colors: [color(0xDCFF4D), color(0xC8FF00), color(0xA6D900)] as CFArray, locations: nil
)!
context.drawLinearGradient(background, start: CGPoint(x: side / 2, y: 0), end: CGPoint(x: side / 2, y: side), options: [])

let ink = color(0x0B0D10)
context.setFillColor(ink)
context.setStrokeColor(ink)
context.setLineCap(.round)
context.setLineJoin(.round)

func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x, y: y) }

/// A limb segment that narrows from one joint to the next, with round ends.
func limb(from start: CGPoint, width startWidth: CGFloat, to end: CGPoint, width endWidth: CGFloat) {
    let length = hypot(end.x - start.x, end.y - start.y)
    // A unit vector across the limb, used to step out to each edge.
    let across = point(-(end.y - start.y) / length, (end.x - start.x) / length)
    let shape = CGMutablePath()
    shape.move(to: point(start.x + across.x * startWidth / 2, start.y + across.y * startWidth / 2))
    shape.addLine(to: point(end.x + across.x * endWidth / 2, end.y + across.y * endWidth / 2))
    shape.addLine(to: point(end.x - across.x * endWidth / 2, end.y - across.y * endWidth / 2))
    shape.addLine(to: point(start.x - across.x * startWidth / 2, start.y - across.y * startWidth / 2))
    shape.closeSubpath()
    context.addPath(shape)
    context.fillPath()
    context.fillEllipse(in: CGRect(x: start.x - startWidth / 2, y: start.y - startWidth / 2, width: startWidth, height: startWidth))
    context.fillEllipse(in: CGRect(x: end.x - endWidth / 2, y: end.y - endWidth / 2, width: endWidth, height: endWidth))
}

/// A muscle or a head: an ellipse turned by `degrees` about its center.
func mass(center: CGPoint, radiusX: CGFloat, radiusY: CGFloat, degrees: CGFloat = 0) {
    context.saveGState()
    context.translateBy(x: center.x, y: center.y)
    context.rotate(by: degrees * .pi / 180)
    context.fillEllipse(in: CGRect(x: -radiusX, y: -radiusY, width: radiusX * 2, height: radiusY * 2))
    context.restoreGState()
}

// Torso: a narrow waist at the bottom edge, out to the lats, then in under the arms.
let torso = CGMutablePath()
torso.move(to: point(424, 1040))
torso.addCurve(to: point(322, 650), control1: point(416, 900), control2: point(348, 780))
torso.addCurve(to: point(342, 500), control1: point(308, 590), control2: point(318, 540))
torso.addLine(to: point(682, 500))
torso.addCurve(to: point(702, 650), control1: point(706, 540), control2: point(716, 590))
torso.addCurve(to: point(600, 1040), control1: point(676, 780), control2: point(608, 900))
torso.closeSubpath()
context.addPath(torso)
context.fillPath()

// Traps: a slope from each shoulder up to the base of the neck.
let traps = CGMutablePath()
traps.move(to: point(342, 508))
traps.addCurve(to: point(476, 436), control1: point(400, 494), control2: point(452, 474))
traps.addLine(to: point(552, 436))
traps.addCurve(to: point(682, 508), control1: point(574, 474), control2: point(624, 494))
traps.closeSubpath()
context.addPath(traps)
context.fillPath()

// Neck, then the head tipped back to look up.
limb(from: point(512, 474), width: 108, to: point(522, 384), width: 88)
mass(center: point(530, 314), radiusX: 70, radiusY: 84, degrees: 14)

// Shoulders.
mass(center: point(338, 514), radiusX: 66, radiusY: 62)
mass(center: point(686, 514), radiusX: 66, radiusY: 62)

// The arm on the left of the picture: raised nearly straight, fist at the top.
limb(from: point(332, 512), width: 104, to: point(270, 304), width: 76)
mass(center: point(296, 408), radiusX: 56, radiusY: 100, degrees: -16)
limb(from: point(270, 304), width: 76, to: point(300, 150), width: 54)
mass(center: point(308, 104), radiusX: 52, radiusY: 54, degrees: 10)

// The arm on the right of the picture: elbow out, forearm folded back toward the head.
limb(from: point(692, 512), width: 104, to: point(812, 352), width: 78)
mass(center: point(742, 424), radiusX: 56, radiusY: 98, degrees: 37)
limb(from: point(812, 352), width: 78, to: point(722, 230), width: 54)
mass(center: point(700, 194), radiusX: 52, radiusY: 54, degrees: -36)

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
