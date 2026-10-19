import 'dart:typed_data';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';

/// 图片处理服务
class ImageService {
  /// 从 Uint8List 创建 ImageProvider
  static ImageProvider fromBytes(Uint8List bytes) {
    return MemoryImage(bytes);
  }

  /// 压缩图片
  static Future<Uint8List> compress(Uint8List bytes, {int quality = 80}) async {
    final codec = await ui.instantiateImageCodec(
      bytes,
      targetWidth: 1024,
      targetHeight: 1024,
    );
    final frame = await codec.getNextFrame();
    final data = await frame.image.toByteData(format: ui.ImageByteFormat.png);
    return data!.buffer.asUint8List();
  }

  /// 去除背景（使用透明度检测，简单实现）
  /// 实际生产中应调用 AI API 实现真正的去背景
  static Future<Uint8List> removeBackground(Uint8List bytes) async {
    // TODO: 接入 rembg API 或类似服务
    return bytes;
  }

  /// 调整图片透明度
  static Future<Uint8List> setOpacity(Uint8List bytes, double opacity) async {
    final codec = await ui.instantiateImageCodec(bytes);
    final frame = await codec.getNextFrame();
    final image = frame.image;

    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);
    final paint = Paint()..colorFilter = ColorFilter.mode(
      Colors.white.withOpacity(opacity),
      BlendMode.modulate,
    );
    canvas.drawImage(image, Offset.zero, paint);

    final picture = recorder.endRecording();
    final result = await picture.toImage(image.width, image.height);
    final data = await result.toByteData(format: ui.ImageByteFormat.png);
    return data!.buffer.asUint8List();
  }
}
