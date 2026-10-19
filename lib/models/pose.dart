import 'dart:typed_data';

/// 姿势模型
class PoseOption {
  final String id;
  final String name;
  final String description;
  final String prompt;

  const PoseOption({
    required this.id,
    required this.name,
    required this.description,
    required this.prompt,
  });

  static final List<PoseOption> poseOptions = [
    PoseOption(
      id: 'standing',
      name: '站立',
      description: '自然站立',
      prompt: 'standing naturally, relaxed posture, full body',
    ),
    PoseOption(
      id: 'peace',
      name: '比耶',
      description: '胜利手势',
      prompt: 'peace sign hand gesture, cheerful pose',
    ),
    PoseOption(
      id: 'sitting',
      name: '坐姿',
      description: '坐在台阶上',
      prompt: 'sitting on stairs, relaxed',
    ),
    PoseOption(
      id: 'thinking',
      name: '思考',
      description: '手托下巴',
      prompt: 'hand on chin, thinking pose',
    ),
    PoseOption(
      id: 'walking',
      name: '走路',
      description: '走路姿态',
      prompt: 'walking forward, dynamic pose',
    ),
    PoseOption(
      id: 'leaning',
      name: '靠墙',
      description: '靠墙站立',
      prompt: 'leaning against wall, casual pose',
    ),
    PoseOption(
      id: 'jumping',
      name: '跳跃',
      description: '跳跃动作',
      prompt: 'jumping in the air, energetic pose',
    ),
    PoseOption(
      id: 'waving',
      name: '挥手',
      description: '打招呼',
      prompt: 'waving hand, greeting pose',
    ),
  ];
}

/// 风格模型
class StyleOption {
  final String id;
  final String name;
  final String prompt;

  const StyleOption({
    required this.id,
    required this.name,
    required this.prompt,
  });

  static final List<StyleOption> styleOptions = [
    StyleOption(
      id: 'anime',
      name: '日系动漫',
      prompt: 'anime style, Studio Ghibli inspired, cel shading',
    ),
    StyleOption(
      id: 'watercolor',
      name: '水彩风',
      prompt: 'watercolor painting style, soft edges, artistic',
    ),
    StyleOption(
      id: 'cyberpunk',
      name: '赛博朋克',
      prompt: 'cyberpunk style, neon lights, futuristic',
    ),
    StyleOption(
      id: 'chibi',
      name: 'Q版大头',
      prompt: 'chibi style, big head small body, cute',
    ),
    StyleOption(
      id: 'sketch',
      name: '铅笔素描',
      prompt: 'pencil sketch, line art, monochrome',
    ),
  ];
}

/// 生成任务状态
enum GenerationStatus {
  idle,
  uploading,
  generating,
  done,
  failed,
}

/// 生成结果
class GenerationResult {
  final Uint8List imageBytes;
  final String? taskId;
  final DateTime createdAt;

  GenerationResult({
    required this.imageBytes,
    this.taskId,
    DateTime? createdAt,
  }) : createdAt = createdAt ?? DateTime.now();
}
