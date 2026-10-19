import 'package:flutter/material.dart';
import '../models/pose.dart';

/// 姿势 + 风格选择器
class PoseSelector extends StatelessWidget {
  final PoseOption currentPose;
  final StyleOption currentStyle;
  final ValueChanged<PoseOption> onPoseChanged;
  final ValueChanged<StyleOption> onStyleChanged;

  const PoseSelector({
    super.key,
    required this.currentPose,
    required this.currentStyle,
    required this.onPoseChanged,
    required this.onStyleChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        // 姿势选择
        const Text(
          '姿势',
          style: TextStyle(color: Colors.white70, fontSize: 12),
        ),
        const SizedBox(height: 6),
        SizedBox(
          height: 60,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            itemCount: PoseOption.poseOptions.length,
            separatorBuilder: (_, __) => const SizedBox(width: 8),
            itemBuilder: (context, index) {
              final pose = PoseOption.poseOptions[index];
              final isSelected = pose.id == currentPose.id;
              return GestureDetector(
                onTap: () => onPoseChanged(pose),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  decoration: BoxDecoration(
                    color: isSelected ? const Color(0xFF4CAF50) : Colors.white12,
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(
                      color: isSelected ? const Color(0xFF4CAF50) : Colors.white24,
                    ),
                  ),
                  child: Center(
                    child: Text(
                      pose.name,
                      style: TextStyle(
                        color: isSelected ? Colors.white : Colors.white70,
                        fontSize: 13,
                        fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                      ),
                    ),
                  ),
                ),
              );
            },
          ),
        ),
        const SizedBox(height: 10),

        // 风格选择
        const Text(
          '风格',
          style: TextStyle(color: Colors.white70, fontSize: 12),
        ),
        const SizedBox(height: 6),
        SizedBox(
          height: 50,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            itemCount: StyleOption.styleOptions.length,
            separatorBuilder: (_, __) => const SizedBox(width: 8),
            itemBuilder: (context, index) {
              final style = StyleOption.styleOptions[index];
              final isSelected = style.id == currentStyle.id;
              return GestureDetector(
                onTap: () => onStyleChanged(style),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                  decoration: BoxDecoration(
                    color: isSelected ? const Color(0xFF2196F3) : Colors.white12,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(
                      color: isSelected ? const Color(0xFF2196F3) : Colors.white24,
                    ),
                  ),
                  child: Center(
                    child: Text(
                      style.name,
                      style: TextStyle(
                        color: isSelected ? Colors.white : Colors.white70,
                        fontSize: 12,
                      ),
                    ),
                  ),
                ),
              );
            },
          ),
        ),
      ],
    );
  }
}
