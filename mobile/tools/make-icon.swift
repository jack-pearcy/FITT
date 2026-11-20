import CoreImage
import Foundation
import ImageIO
import UniformTypeIdentifiers
import Vision

// Draws the app icon from a photo of a person and writes it as a PNG.
//
//   swift mobile/tools/make-icon.swift <photo> mobile/App/Assets.xcassets/AppIcon.appiconset/icon-1024.png 1024 0 36
//
// Arguments after the output path: size in pixels, how far to grow the arms,
// shoulders and lats, and how far to pull in the waist (both in photo pixels,
// 0 for none). The shipped icon uses 1024, 0 and 36.
//
// The person is cut out with the system's person detector and filled with the
// app's near-black on the accent color. The photo is the developer's own and is
// kept outside the repository. The regions for the two adjustments are
// fractions of that photo, so another photo needs its own regions.
// iOS wants one square 1024 pixel image with no transparency; the system
// rounds the corners.

let arguments = CommandLine.arguments
guard arguments.count >= 3 else {
    print("usage: make-icon.swift <photo> <output.png> [size] [grow] [slim]")
    exit(1)
}
let size = arguments.count > 3 ? Int(arguments[3]) ?? 1024 : 1024

let photoURL = URL(fileURLWithPath: arguments[1])
guard let photo = CIImage(contentsOf: photoURL, options: [.applyOrientationProperty: true]) else {
    print("cannot read photo")
    exit(1)
}
let ciContext = CIContext()

// 1. Person mask from Vision.
let request = VNGeneratePersonSegmentationRequest()
request.qualityLevel = .accurate
request.outputPixelFormat = kCVPixelFormatType_OneComponent8
try VNImageRequestHandler(ciImage: photo).perform([request])
guard let buffer = request.results?.first?.pixelBuffer else {
    print("no person found")
    exit(1)
}

// 2. Scale the mask to the photo, smooth it, and cut it to pure black and white.
var mask = CIImage(cvPixelBuffer: buffer)
mask = mask.transformed(by: CGAffineTransform(
    scaleX: photo.extent.width / mask.extent.width,
    y: photo.extent.height / mask.extent.height
))
let extent = CGRect(origin: .zero, size: photo.extent.size)
mask = mask
    .applyingFilter("CIMaximumComponent")
    .clampedToExtent()
    .applyingGaussianBlur(sigma: Double(extent.width) * 0.003)
    .applyingFilter("CIColorThreshold", parameters: ["inputThreshold": 0.5])
    .cropped(to: extent)
guard let maskImage = ciContext.createCGImage(mask, from: extent) else {
    print("cannot render mask")
    exit(1)
}

// 3. Find the person's bounds, in top-left coordinates.
let width = maskImage.width, height = maskImage.height
let gray = CGColorSpaceCreateDeviceGray()
let scan = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width,
                     space: gray, bitmapInfo: CGImageAlphaInfo.none.rawValue)!
scan.draw(maskImage, in: CGRect(x: 0, y: 0, width: width, height: height))
let pixels = scan.data!.bindMemory(to: UInt8.self, capacity: width * height)
var minX = width, maxX = 0, minY = height, maxY = 0
for y in 0..<height {
    for x in 0..<width where pixels[y * width + x] > 127 {
        minX = min(minX, x); maxX = max(maxX, x)
        minY = min(minY, y); maxY = max(maxY, y)
    }
}
guard maxX > minX else {
    print("empty mask")
    exit(1)
}

// Fill holes: anything the background cannot reach from the border is figure.
var outside = [Bool](repeating: false, count: width * height)
var stack: [Int] = []
func visit(_ i: Int) {
    if !outside[i] && pixels[i] <= 127 { outside[i] = true; stack.append(i) }
}
for x in 0..<width { visit(x); visit((height - 1) * width + x) }
for y in 0..<height { visit(y * width); visit(y * width + width - 1) }
while let i = stack.popLast() {
    let x = i % width, y = i / width
    if x > 0 { visit(i - 1) }
    if x < width - 1 { visit(i + 1) }
    if y > 0 { visit(i - width) }
    if y < height - 1 { visit(i + width) }
}
for i in 0..<(width * height) { pixels[i] = outside[i] ? 0 : 255 }
var solidMask = scan.makeImage()!

// Optional: grow the outline a little over the arms, shoulders and lats only.
// Regions are (centerX, centerY, radiusX, radiusY, strength) as fractions of the photo.
let buff = arguments.count > 4 ? Double(arguments[4]) ?? 0 : 0
if buff > 0 {
    let regions: [(Double, Double, Double, Double, Double)] = [
        (0.287, 0.505, 0.150, 0.080, 1.0),  // left upper arm
        (0.193, 0.400, 0.075, 0.100, 0.45),  // left forearm
        (0.853, 0.565, 0.155, 0.080, 1.0),  // right upper arm
        (0.913, 0.475, 0.080, 0.085, 0.45),  // right forearm
        (0.407, 0.530, 0.075, 0.045, 1.0),  // left shoulder
        (0.727, 0.530, 0.075, 0.045, 1.0),  // right shoulder
        (0.385, 0.640, 0.070, 0.090, 0.6),  // left lat
        (0.755, 0.640, 0.070, 0.090, 0.6),  // right lat
    ]
    let grown = CIImage(cgImage: solidMask)
        .applyingFilter("CIMorphologyMaximum", parameters: ["inputRadius": buff])
        .cropped(to: extent)
    let grownImage = ciContext.createCGImage(grown, from: extent)!
    let grownScan = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width,
                              space: gray, bitmapInfo: CGImageAlphaInfo.none.rawValue)!
    grownScan.draw(grownImage, in: CGRect(x: 0, y: 0, width: width, height: height))
    let grownPixels = grownScan.data!.bindMemory(to: UInt8.self, capacity: width * height)
    for y in 0..<height {
        let v = Double(y) / Double(height)
        for x in 0..<width {
            let i = y * width + x
            guard pixels[i] < 128, grownPixels[i] > 127 else { continue }
            let u = Double(x) / Double(width)
            var weight = 0.0
            for (cx, cy, rx, ry, strength) in regions {
                let dx = (u - cx) / rx, dy = (v - cy) / ry
                weight = max(weight, strength * exp(-(dx * dx + dy * dy)))
            }
            pixels[i] = UInt8(min(255, weight * 255 * 1.4))
        }
    }
    // Blend the grown ring into the outline with one more smooth and cut.
    let blended = CIImage(cgImage: scan.makeImage()!)
        .clampedToExtent()
        .applyingGaussianBlur(sigma: buff * 0.5)
        .applyingFilter("CIColorThreshold", parameters: ["inputThreshold": 0.5])
        .cropped(to: extent)
        // Keep every detail of the first outline, such as the fingers.
        .applyingFilter("CIMaximumCompositing", parameters: ["inputBackgroundImage": CIImage(cgImage: solidMask)])
    solidMask = ciContext.createCGImage(blended, from: extent)!
}
print("person bounds: x \(minX)-\(maxX), y \(minY)-\(maxY) of \(width)x\(height)")

// Optional: pull the outline in a little at the sides of the waist only.
let slim = arguments.count > 5 ? Double(arguments[5]) ?? 0 : 0
if slim > 0 {
    let regions: [(Double, Double, Double, Double)] = [
        (0.400, 0.810, 0.080, 0.110),  // left side of the waist
        (0.727, 0.810, 0.080, 0.110),  // right side of the waist
    ]
    func weight(_ x: Int, _ y: Int) -> Double {
        let u = Double(x) / Double(width), v = Double(y) / Double(height)
        var result = 0.0
        for (cx, cy, rx, ry) in regions {
            let dx = (u - cx) / rx, dy = (v - cy) / ry
            result = max(result, exp(-(dx * dx + dy * dy)))
        }
        return result
    }
    func bitmap(_ image: CGImage) -> CGContext {
        let context = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width,
                                space: gray, bitmapInfo: CGImageAlphaInfo.none.rawValue)!
        context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        return context
    }
    let base = bitmap(solidMask)
    let basePixels = base.data!.bindMemory(to: UInt8.self, capacity: width * height)
    let shrunk = CIImage(cgImage: solidMask)
        .applyingFilter("CIMorphologyMinimum", parameters: ["inputRadius": slim])
        .cropped(to: extent)
    let shrunkBitmap = bitmap(ciContext.createCGImage(shrunk, from: extent)!)
    let shrunkPixels = shrunkBitmap
        .data!.bindMemory(to: UInt8.self, capacity: width * height)

    let mixed = bitmap(solidMask)
    let mixedPixels = mixed.data!.bindMemory(to: UInt8.self, capacity: width * height)
    for y in 0..<height {
        for x in 0..<width {
            let i = y * width + x
            guard basePixels[i] > 127, shrunkPixels[i] < 128 else { continue }
            mixedPixels[i] = UInt8(255 * (1 - min(1, weight(x, y) * 1.4)))
        }
    }
    let blended = CIImage(cgImage: mixed.makeImage()!)
        .clampedToExtent()
        .applyingGaussianBlur(sigma: slim * 0.5)
        .applyingFilter("CIColorThreshold", parameters: ["inputThreshold": 0.5])
        .cropped(to: extent)
    let blendedBitmap = bitmap(ciContext.createCGImage(blended, from: extent)!)
    let blendedPixels = blendedBitmap
        .data!.bindMemory(to: UInt8.self, capacity: width * height)
    // Away from the waist, keep the first outline exactly.
    for y in 0..<height {
        for x in 0..<width where weight(x, y) > 0.05 {
            basePixels[y * width + x] = blendedPixels[y * width + x]
        }
    }
    solidMask = base.makeImage()!
    withExtendedLifetime((shrunkBitmap, blendedBitmap)) {}
}

// 4. A square window: the full width of the person with a margin, starting just
// above the highest point. Whatever is below the window runs off the bottom edge.
let margin = Double(maxX - minX) * 0.07
let side = Double(maxX - minX) + margin * 2
let windowX = Double(minX) - margin
let windowY = Double(minY) - margin
let scale = Double(size) / side

// 5. The mask placed in the icon's frame.
let placed = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: size,
                       space: gray, bitmapInfo: CGImageAlphaInfo.none.rawValue)!
placed.setFillColor(gray: 0, alpha: 1)
placed.fill(CGRect(x: 0, y: 0, width: size, height: size))
placed.interpolationQuality = .high
placed.draw(solidMask, in: CGRect(
    x: -windowX * scale,
    y: Double(size) + windowY * scale - Double(height) * scale,
    width: Double(width) * scale,
    height: Double(height) * scale
))
let clip = placed.makeImage()!

// 6. Lime background, near-black figure, no alpha channel.
let rgb = CGColorSpace(name: CGColorSpace.sRGB)!
let icon = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
                     space: rgb, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
let full = CGRect(x: 0, y: 0, width: size, height: size)
func color(_ hex: UInt32) -> CGColor {
    CGColor(colorSpace: rgb, components: [
        CGFloat((hex >> 16) & 0xFF) / 255, CGFloat((hex >> 8) & 0xFF) / 255, CGFloat(hex & 0xFF) / 255, 1,
    ])!
}
let gradient = CGGradient(colorsSpace: rgb, colors: [color(0xD6FF3D), color(0xB4E600)] as CFArray, locations: [0, 1])!
icon.drawLinearGradient(gradient, start: CGPoint(x: 0, y: size), end: CGPoint(x: 0, y: 0), options: [])
icon.clip(to: full, mask: clip)
icon.setFillColor(color(0x0B0D10))
icon.fill(full)

let destination = CGImageDestinationCreateWithURL(
    URL(fileURLWithPath: arguments[2]) as CFURL, UTType.png.identifier as CFString, 1, nil
)!
CGImageDestinationAddImage(destination, icon.makeImage()!, nil)
guard CGImageDestinationFinalize(destination) else {
    print("cannot write icon")
    exit(1)
}
print("wrote \(arguments[2])")
