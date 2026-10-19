import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';

/// 预览页面 — 合成照片预览 + 保存 + 分享
class PreviewScreen extends StatefulWidget {
  final String cameraImagePath;
  final Uint8List characterImage;
  final Offset characterPosition;
  final double characterScale;

  const PreviewScreen({
    super.key,
    required this.cameraImagePath,
    required this.characterImage,
    required this.characterPosition,
    required this.characterScale,
  });

  @override
  State<PreviewScreen> createState() => _PreviewScreenState();
}

class _PreviewScreenState extends State<PreviewScreen> {
  bool _isSaving = false;
  final GlobalKey _repaintBoundaryKey = GlobalKey();

  /// 合成两张图片
  Future<Uint8List> _composeImages() async {
    // 加载摄像头照片
    final cameraFile = File(widget.cameraImagePath);
    final cameraBytes = await cameraFile.readAsBytes();

    final cameraCodec = await ui.instantiateImageCodec(cameraBytes);
    final cameraFrame = await cameraCodec.getNextFrame();
    final cameraImg = cameraFrame.image;

    // 解码人物图片
    final charCodec = await ui.instantiateImageCodec(widget.characterImage);
    final charFrame = await charCodec.getNextFrame();
    final charImg = charFrame.image;

    // 创建画布
    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);

    // 1. 绘制背景（摄像头照片）
    canvas.drawImage(
      cameraImg,
      Offset.zero,
      Paint()..filterQuality = FilterQuality.high,
    );

    // 2. 计算人物位置和大小
    final canvasW = cameraImg.width.toDouble();
    final canvasH = cameraImg.height.toDouble();
    final charWidth = canvasW * widget.characterScale;
    final charHeight = charWidth * (charImg.height / charImg.width);

    final left = widget.characterPosition.dx * canvasW - charWidth / 2;
    final top = widget.characterPosition.dy * canvasH - charHeight / 2;

    // 3. 绘制人物
    canvas.drawImageRect(
      charImg,
      Rect.fromLTWH(0, 0, charImg.width.toDouble(), charImg.height.toDouble()),
      Rect.fromLTWH(left, top, charWidth, charHeight),
      Paint()..filterQuality = FilterQuality.high,
    );

    // 4. 输出
    final picture = recorder.endRecording();
    final composedImage = await picture.toImage(cameraImg.width, cameraImg.height);
    final byteData = await composedImage.toByteData(format: ui.ImageByteFormat.png);

    return byteData!.buffer.asUint8List();
  }

  /// 保存到本地
  Future<void> _savePhoto() async {
    setState(() => _isSaving = true);
    try {
      final bytes = await _composeImages();
      final dir = await getApplicationDocumentsDirectory();
      final timestamp = DateTime.now().millisecondsSinceEpoch;
      final file = File('${dir.path}/photogenic_$timestamp.png');
      await file.writeAsBytes(bytes);

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('已保存到: ${file.path}'),
            backgroundColor: Colors.green,
            duration: const Duration(seconds: 2),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('保存失败: $e'), backgroundColor: Colors.red),
        );
      }
    } finally {
      if (mounted) setState(() => _isSaving = false);
    }
  }

  /// 分享
  Future<void> _sharePhoto() async {
    setState(() => _isSaving = true);
    try {
      final bytes = await _composeImages();
      final dir = await getTemporaryDirectory();
      final timestamp = DateTime.now().millisecondsSinceEpoch;
      final file = File('${dir.path}/photogenic_$timestamp.png');
      await file.writeAsBytes(bytes);

      await Share.shareXFiles(
        [XFile(file.path)],
        text: 'AI 踩点拍照 ✨',
      );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('分享失败: $e'), backgroundColor: Colors.red),
        );
      }
    } finally {
      if (mounted) setState(() => _isSaving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          // 合成预览
          Positioned.fill(
            child: RepaintBoundary(
              key: _repaintBoundaryKey,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  // 背景照片
                  Image.file(
                    File(widget.cameraImagePath),
                    fit: BoxFit.cover,
                  ),
                  // 人物叠加
                  _buildCharacterOverlay(),
                ],
              ),
            ),
          ),

          // 顶部栏
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Row(
                children: [
                  IconButton(
                    onPressed: () => Navigator.pop(context),
                    icon: const Icon(Icons.close, color: Colors.white, size: 28),
                  ),
                  const Spacer(),
                  if (_isSaving)
                    const Padding(
                      padding: EdgeInsets.all(8),
                      child: SizedBox(
                        width: 24,
                        height: 24,
                        child: CircularProgressIndicator(
                          color: Colors.white,
                          strokeWidth: 2,
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),

          // 底部按钮
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: SafeArea(
              child: Container(
                padding: const EdgeInsets.all(20),
                decoration: const BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.bottomCenter,
                    end: Alignment.topCenter,
                    colors: [Colors.black87, Colors.transparent],
                  ),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: [
                    // 保存
                    Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        IconButton(
                          onPressed: _isSaving ? null : _savePhoto,
                          icon: const Icon(Icons.save_alt, color: Colors.white, size: 32),
                        ),
                        const Text('保存', style: TextStyle(color: Colors.white, fontSize: 12)),
                      ],
                    ),
                    // 重新拍照
                    Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        IconButton(
                          onPressed: () => Navigator.pop(context),
                          icon: const Icon(Icons.refresh, color: Colors.white, size: 32),
                        ),
                        const Text('重拍', style: TextStyle(color: Colors.white, fontSize: 12)),
                      ],
                    ),
                    // 分享
                    Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        IconButton(
                          onPressed: _isSaving ? null : _sharePhoto,
                          icon: const Icon(Icons.share, color: Colors.white, size: 32),
                        ),
                        const Text('分享', style: TextStyle(color: Colors.white, fontSize: 12)),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCharacterOverlay() {
    // 使用 LayoutBuilder 计算人物位置
    return LayoutBuilder(
      builder: (context, constraints) {
        final w = constraints.maxWidth;
        final h = constraints.maxHeight;
        final charWidth = w * widget.characterScale;
        final charHeight = charWidth * 1.4;
        final left = widget.characterPosition.dx * w - charWidth / 2;
        final top = widget.characterPosition.dy * h - charHeight / 2;

        return Positioned(
          left: left,
          top: top,
          width: charWidth,
          height: charHeight,
          child: Image.memory(
            widget.characterImage,
            fit: BoxFit.contain,
          ),
        );
      },
    );
  }
}
