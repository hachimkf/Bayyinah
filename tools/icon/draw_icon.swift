import AppKit
// Recreates the user's minimal icon (1254×1254 design space). Output: fg.png (transparent), full.png
let S: CGFloat = 1254
func c(_ hex: UInt32) -> NSColor { NSColor(srgbRed: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: 1) }
let dark = c(0x033D0B), page = c(0x00C72B), lime = c(0x8EFF00)
func render(_ file: String, background: Bool) {
    let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: Int(S), pixelsHigh: Int(S), bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    NSGraphicsContext.saveGraphicsState(); NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    let ctx = NSGraphicsContext.current!.cgContext
    ctx.translateBy(x: 0, y: S); ctx.scaleBy(x: 1, y: -1)   // top-left origin like the design
    if background {
        NSGradient(starting: c(0x00BE20), ending: c(0x009B33))!.draw(in: NSRect(x: 0, y: 0, width: S, height: S), angle: -45)
    }
    func rr(_ x0: CGFloat, _ y0: CGFloat, _ x1: CGFloat, _ y1: CGFloat, _ r: CGFloat, _ col: NSColor) {
        col.setFill(); NSBezierPath(roundedRect: NSRect(x: x0, y: y0, width: x1 - x0, height: y1 - y0), xRadius: r, yRadius: r).fill()
    }
    rr(322, 312, 932, 978, 46, dark)      // back cover
    rr(350, 326, 912, 958, 26, page)      // page edge (bright strip right + bottom)
    rr(322, 274, 890, 925, 46, dark)      // front cover
    // scan corners
    lime.setStroke()
    let x0: CGFloat = 431, y0: CGFloat = 463, x1: CGFloat = 815, y1: CGFloat = 792, arm: CGFloat = 96, r: CGFloat = 44
    func corner(_ ax: CGFloat, _ ay: CGFloat, _ sx: CGFloat, _ sy: CGFloat) {
        let p = NSBezierPath(); p.lineWidth = 22; p.lineCapStyle = .round
        p.move(to: NSPoint(x: ax, y: ay + sy * arm)); p.line(to: NSPoint(x: ax, y: ay + sy * r))
        p.curve(to: NSPoint(x: ax + sx * r, y: ay), controlPoint1: NSPoint(x: ax, y: ay + sy * r * 0.45), controlPoint2: NSPoint(x: ax + sx * r * 0.45, y: ay))
        p.line(to: NSPoint(x: ax + sx * arm, y: ay)); p.stroke()
    }
    corner(x0, y0, 1, 1); corner(x1, y0, -1, 1); corner(x0, y1, 1, -1); corner(x1, y1, -1, -1)
    // «القرآن»
    ctx.saveGState(); ctx.translateBy(x: 0, y: S); ctx.scaleBy(x: 1, y: -1)   // back to AppKit coords for text
    let font = NSFont(name: "DiwanThuluth", size: 270)!
    let s = NSAttributedString(string: "القرآن", attributes: [.font: font, .foregroundColor: lime, .strokeColor: lime, .strokeWidth: -2.5])
    let b = s.boundingRect(with: NSSize(width: S, height: S), options: [.usesLineFragmentOrigin, .usesDeviceMetrics])
    let cx: CGFloat = 632, cy: CGFloat = S - 618 - 126
    s.draw(at: NSPoint(x: cx - b.width / 2 - b.minX, y: cy - b.height / 2 - b.minY))
    ctx.restoreGState()
    NSGraphicsContext.restoreGraphicsState()
    try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: file))
}
render("fg.png", background: false); render("full.png", background: true); print("ok")
