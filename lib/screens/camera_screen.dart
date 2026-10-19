import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:camera/camera.dart';
import '../widgets/pose_selector.dart';
import '../widgets/character_overlay.dart';
import '../models/pose.dart';
import '../services/aigc_service.dart';
import 'preview_screen.dart';

/// 拍照页面 — 摄像头预览 + 2D 人物叠加
class CameraScreen extends StatefulWidget {
  final Uint8List faceImage;

  const CameraScreen({super.key, required this.faceImage});

  @override
  State<CameraScreen> createState() => _CameraScreenState();
}

class _CameraScreenState extends State<CameraScreen> {
  CameraController? _cameraController;
  List<CameraDescription> _cameras = [];
  bool _cameraReady = false;

  // 当前选中的姿势和风格
  PoseOption _currentPose = PoseOption.poseOptions[0];
  StyleOption _currentStyle = StyleOption.styleOptions[0];

  // 生成的 2D 人物
  Uint8List? _characterImage;
  bool _isGenerating = false;
  double _generateProgress = 0.0;

  // 人物位置和大小
  Offset _characterPosition = const Offset(0.5, 0.6);
  double _characterScale = 0.6;

  // AI 服务
  late final AigcService _aigcService;

  @override
  void initState() {
    super.initState();
    // 默认使用本地模拟模式（MVP 阶段，不实际调用 API）
    _aigcService = AigcService(
      config: const AigcConfig(
        apiUrl: 'http://localhost:7860',
        apiKey: '',
        model: 'sdxl',
      ),
    );
    _initCamera();
  }

  Future<void> _initCamera() async {
    try {
      _cameras = await availableCameras();
      if (_cameras.isEmpty) {
        _showError('没有可用的摄像头');
        return;
      }

      // 默认用后置摄像头
      final backCamera = _cameras.firstWhere(
        (c) => c.lensDirection == CameraLensDirection.back,
        orElse: () => _cameras[0],
      );

      _cameraController = CameraController(
        backCamera,
        ResolutionPreset.high,
        enableAudio: false,
      );

      await _cameraController!.initialize();
      if (mounted) {
        setState(() => _cameraReady = true);
      }
    } catch (e) {
      _showError('摄像头初始化失败: $e');
    }
  }

  void _showError(String msg) {
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(msg), backgroundColor: Colors.red),
      );
    }
  }

  /// 生成 2D 人物
  Future<void> _generateCharacter() async {
    setState(() {
      _isGenerating = true;
      _generateProgress = 0.0;
    });

    try {
      // MVP 模式：使用本地占位图（实际接入时调用 API）
      // final result = await _aigcService.generate2DCharacter(
      //   faceImage: widget.faceImage,
      //   posePrompt: _currentPose.prompt,
      //   stylePrompt: _currentStyle.prompt,
      //   onProgress: (p) => setState(() => _generateProgress = p),
      // );

      // 模拟生成过程（MVP 阶段）
      for (double p = 0.0; p <= 1.0; p += 0.1) {
        await Future.delayed(const Duration(milliseconds: 200));
        if (mounted) setState(() => _generateProgress = p);
      }

      // 使用用户上传的照片作为临时占位
      setState(() {
        _characterImage = widget.faceImage;
      });
    } catch (e) {
      _showError('生成失败: $e');
    } finally {
      if (mounted) setState(() => _isGenerating = false);
    }
  }

  /// 拍照（合成照片）
  Future<void> _capturePhoto() async {
    if (_characterImage == null) {
      _showError('请先生成 2D 人物');
      return;
    }

    try {
      final XFile photo = await _cameraController!.takePicture();

      if (mounted) {
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (_) => PreviewScreen(
              cameraImagePath: photo.path,
              characterImage: _characterImage!,
              characterPosition: _characterPosition,
              characterScale: _characterScale,
            ),
          ),
        );
      }
    } catch (e) {
      _showError('拍照失败: $e');
    }
  }

  @override
  void dispose() {
    _cameraController?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          // 摄像头预览
          if (_cameraReady && _cameraController != null)
            Positioned.fill(
              child: CameraPreview(_cameraController!),
            )
          else
            const Center(child: CircularProgressIndicator(color: Colors.white)),

          // 2D 人物叠加层
          if (_characterImage != null)
            CharacterOverlay(
              imageBytes: _characterImage!,
              position: _characterPosition,
              scale: _characterScale,
              onPositionChanged: (pos) => setState(() => _characterPosition = pos),
              onScaleChanged: (s) => setState(() => _characterScale = s),
            ),

          // 顶部工具栏
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Row(
                children: [
                  IconButton(
                    onPressed: () => Navigator.pop(context),
                    icon: const Icon(Icons.arrow_back, color: Colors.white, size: 28),
                  ),
                  const Spacer(),
                  // 切换摄像头
                  if (_cameras.length > 1)
                    IconButton(
                      onPressed: _switchCamera,
                      icon: const Icon(Icons.flip_camera_ios, color: Colors.white, size: 28),
                    ),
                ],
              ),
            ),
          ),

          // 底部控制栏
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: SafeArea(
              child: Container(
                padding: const EdgeInsets.all(16),
                decoration: const BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.bottomCenter,
                    end: Alignment.topCenter,
                    colors: [Colors.black87, Colors.transparent],
                  ),
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    // 姿势选择器
                    PoseSelector(
                      currentPose: _currentPose,
                      currentStyle: _currentStyle,
                      onPoseChanged: (pose) => setState(() => _currentPose = pose),
                      onStyleChanged: (style) => setState(() => _currentStyle = style),
                    ),
                    const SizedBox(height: 12),

                    // 生成按钮 + 拍照按钮
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                      children: [
                        // 生成 2D 人物按钮
                        Column(
                          children: [
                            FloatingActionButton(
                              heroTag: 'generate',
                              onPressed: _isGenerating ? null : _generateCharacter,
                              backgroundColor: const Color(0xFF4CAF50),
                              child: _isGenerating
                                  ? CircularProgressIndicator(
                                      value: _generateProgress,
                                      color: Colors.white,
                                      strokeWidth: 3,
                                    )
                                  : const Icon(Icons.auto_awesome, color: Colors.white),
                            ),
                            const SizedBox(height: 6),
                            const Text(
                              _generateText(),
                              style: TextStyle(color: Colors.white, fontSize: 12),
                            ),
                          ],
                        ),

                        // 拍照按钮
                        Column(
                          children: [
                            GestureDetector(
                              onTap: _capturePhoto,
                              child: Container(
                                width: 72,
                                height: 72,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  border: Border.all(color: Colors.white, width: 4),
                                  color: _characterImage != null
                                      ? Colors.white
                                      : Colors.white24,
                                ),
                                child: Icon(
                                  Icons.camera,
                                  color: _characterImage != null
                                      ? Colors.black
                                      : Colors.white54,
                                  size: 32,
                                ),
                              ),
                            ),
                            const SizedBox(height: 6),
                            const Text(
                              '拍照',
                              style: TextStyle(color: Colors.white, fontSize: 12),
                            ),
                          ],
                        ),
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

  String _generateText() {
    if (_isGenerating) {
      return '${(_generateProgress * 100).toInt()}%';
    }
    if (_characterImage != null) {
      return '换姿势';
    }
    return '生成人物';
  }

  Future<void> _switchCamera() async {
    if (_cameras.length < 2) return;
    final currentLens = _cameraController!.description.lensDirection;
    final newCamera = _cameras.firstWhere(
      (c) => c.lensDirection != currentLens,
      orElse: () => _cameras[0],
    );
    await _cameraController!.setDescription(newCamera);
    setState(() {});
  }
}
