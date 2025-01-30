//编译： swiftc VisionOCR.swift -o vision-ocr -framework Vision -framework Cocoa
import Foundation
import Vision
import AppKit

// 1. 从命令行参数获取图片路径
guard CommandLine.arguments.count > 1 else {
    print("Usage: VisionOCR <image_path>")
    exit(1)
}
let imagePath = CommandLine.arguments[1]

// 2. 加载图片
guard let nsImage = NSImage(contentsOfFile: imagePath) else {
    print("Error: Failed to load image.")
    exit(2)
}

// 将 NSImage 转换为 CGImage
guard let cgImage = nsImage.cgImage(forProposedRect: nil, context: nil, hints: nil) else {
    print("Error: Failed to convert NSImage to CGImage.")
    exit(2)
}

// 3. 调用 Vision 框架识别文字
let request = VNRecognizeTextRequest { (request, error) in
    guard let observations = request.results as? [VNRecognizedTextObservation] else {
        print("OCR Error: \(error?.localizedDescription ?? "Unknown error")")
        exit(3)
    }
    
    let texts = observations.compactMap { $0.topCandidates(1).first?.string }
    print(texts.joined(separator: "\n"))  // 输出识别结果
    
    // 使用异步回调退出主程序
    DispatchQueue.main.async {
        exit(0)
    }
}
request.recognitionLanguages = ["zh-Hans", "en"]  // 指定中英文

// 4. 执行请求
let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
do {
    try handler.perform([request])
} catch {
    print("Error: Failed to perform OCR request. \(error.localizedDescription)")
    exit(3)
}

// 5. 保持运行直到完成
RunLoop.current.run()

