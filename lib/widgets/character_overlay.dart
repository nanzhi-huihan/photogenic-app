import 'dart:typed_data';
import 'package:flutter/material.dart';

/// 2D 人物叠加层 — 支持拖拽和缩放
class CharacterOverlay extends StatefulWidget {
  final Uint8List imageBytes;
  final Offset position; // 0.0 ~ 1.0 的相对位置
  final double scale;
  final ValueChanged<Offset> onPositionChanged;
  final ValueChanged<double> onScaleChanged;

  const CharacterOverlay({
    super.key,
    required this.imageBytes,
    required this.position,
    required this.scale,
    required this.onPositionChanged,
    required this.onScaleChanged,
  });

  @override
  State<CharacterOverlay> createState() => _CharacterOverlayState();
}

class _CharacterOverlayState extends State<CharacterOverlay> {
  late Offset _position;
  late double _scale;
  Offset? _lastFocalPoint;

  @override
  void initState() {
    super.initState();
    _position = widget.position;
    _scale = widget.scale;
  }

  @override
  void didUpdateWidget(CharacterOverlay oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.position != widget.position) {
      _position = widget.position;
    }
    if (oldWidget.scale != widget.scale) {
      _scale = widget.scale;
    }
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        // 计算人物在屏幕上的实际位置和大小
        final screenW = constraints.maxWidth;
        final screenH = constraints.maxHeight;

        final charWidth = screenW * _scale;
        final charHeight = charWidth * 1.4; // 人物宽高比 1:1.4

        final left = _position.dx * screenW - charWidth / 2;
        final top = _position.dy * screenH - charHeight / 2;

        return Positioned(
          left: left.clamp(-charWidth * 0.3, screenW - charWidth * 0.7),
          top: top.clamp(-charHeight * 0.2, screenH - charHeight * 0.5),
          width: charWidth,
          height: charHeight,
          child: GestureDetector(
            onScaleStart: (details) {
              _lastFocalPoint = details.focalPoint;
            },
            onScaleUpdate: (details) {
              setState(() {
                // 缩放
                if (details.scale != 1.0) {
                  _scale = (_scale * details.scale).clamp(0.3, 1.2);
                  widget.onScaleChanged(_scale);
                }

                // 拖拽
                if (_lastFocalPoint != null) {
                  final delta = details.focalPoint - _lastFocalPoint!;
                  final newX = _position.dx + delta.dx / screenW;
                  final newY = _position.dy + delta.dy / screenH;
                  _position = Offset(
                    newX.clamp(0.1, 0.9),
                    newY.clamp(0.2, 0.95),
                  );
                  widget.onPositionChanged(_position);
                  _lastFocalPoint = details.focalPoint;
                }
              });
            },
            child: Image.memory(
              widget.imageBytes,
              fit: BoxFit.contain,
              gaplessPlayback: true,
            ),
          ),
        );
      },
    );
  }
}
