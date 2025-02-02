//swiftc VisionOCR.swift -o vision-ocr -framework Vision -framework Cocoa
import Foundation
import Vision
import AppKit

// 1. 从标准输入读取图片数据
let fileHandle = FileHandle.standardInput
guard let imageData = try? fileHandle.readToEnd(), !imageData.isEmpty else {
    print("Error: Failed to read image from standard input.")
    exit(1)
}

// 2. 加载图片
guard let nsImage = NSImage(data: imageData) else {
    print("Error: Failed to load image.")
    exit(2)
}

// 获取图像的宽度和高度
let imageWidth = nsImage.size.width
let imageHeight = nsImage.size.height

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
    
    // 创建一个数组存储识别结果
    var results: [[String: Any]] = []
    
    // 遍历每个文本块并提取文本和其位置
    for observation in observations {
        // 获取文本内容
        if let recognizedText = observation.topCandidates(1).first?.string {
            // 获取归一化的bounding box
            let boundingBox = observation.boundingBox
            
            // 如果需要将归一化的 boundingBox 转换为实际像素坐标
            let actualBoundingBox = CGRect(
                x: boundingBox.origin.x * CGFloat(imageWidth),
                y: (1 - boundingBox.origin.y - boundingBox.height) * CGFloat(imageHeight),
                width: boundingBox.width * CGFloat(imageWidth),
                height: boundingBox.height * CGFloat(imageHeight)
            )
            
            // 添加文本和位置数据到结果数组
            let result: [String: Any] = [
                "text": recognizedText,
                "boundingBox": [
                    "x": Int(actualBoundingBox.origin.x),
                    "y": Int(actualBoundingBox.origin.y),
                    "width": Int(actualBoundingBox.width),
                    "height": Int(actualBoundingBox.height)
                ]
            ]
            results.append(result)
        }
    }
    
    // 创建包含图像尺寸和识别结果的字典
    let output: [String: Any] = [
        "imageWidth": imageWidth,
        "imageHeight": imageHeight,
        "results": results
    ]
    
    // 将识别结果转换为JSON格式并输出
    if let jsonData = try? JSONSerialization.data(withJSONObject: output, options: .prettyPrinted),
       let jsonString = String(data: jsonData, encoding: .utf8) {
        print(jsonString)
    } else {
        print("Error: Failed to serialize results to JSON.")
    }
    
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

