import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';
import 'package:http/http.dart' as http;
import '../models/pose.dart';

/// AIGC API 配置
class AigcConfig {
  final String apiUrl;
  final String apiKey;
  final String model;

  const AigcConfig({
    required this.apiUrl,
    required this.apiKey,
    this.model = 'sdxl',
  });
}

/// AIGC 服务 — 调用第三方图生图 API
/// 支持：Stable Diffusion API / Replicate / 通义万相 / 文心一格 等
class AigcService {
  final AigcConfig config;

  AigcService({required this.config});

  /// 生成 2D 人物
  /// [faceImage] - 用户上传的正面照
  /// [posePrompt] - 姿势描述
  /// [stylePrompt] - 风格描述
  Future<Uint8List> generate2DCharacter({
    required Uint8List faceImage,
    required String posePrompt,
    required String stylePrompt,
    void Function(double progress)? onProgress,
  }) async {
    onProgress?.call(0.1);

    // 构建 prompt
    final fullPrompt = _buildPrompt(posePrompt, stylePrompt);
    final negativePrompt = _negativePrompt;

    onProgress?.call(0.2);

    // 调用 API
    final response = await _callApi(
      faceImage: faceImage,
      prompt: fullPrompt,
      negativePrompt: negativePrompt,
      onProgress: onProgress,
    );

    onProgress?.call(0.9);

    // 解码返回的图片
    final imageBytes = _decodeResponse(response);

    onProgress?.call(1.0);
    return imageBytes;
  }

  /// 构建正向 prompt
  String _buildPrompt(String posePrompt, String stylePrompt) {
    return '''
2D anime style character, full body, $posePrompt,
standing pose, high quality, detailed illustration,
soft lighting, vibrant colors, cute, $posePrompt,
transparent background, clean lines, manga style,
$stylePrompt, masterpiece, best quality
''';
  }

  /// 负向 prompt
  static const String _negativePrompt = '''
3D, realistic, photo, deformed, blurry, bad anatomy,
extra limbs, ugly, poorly drawn, watermark, text,
low quality, worst quality, out of frame, oversaturated
''';

  /// 调用 AIGC API
  Future<dynamic> _callApi({
    required Uint8List faceImage,
    required String prompt,
    required String negativePrompt,
    void Function(double progress)? onProgress,
  }) async {
    // 方式 1: Stable Diffusion WebUI API (Img2Img with IP-Adapter face)
    if (config.model == 'sdxl') {
      return await _callStableDiffusionApi(
        faceImage: faceImage,
        prompt: prompt,
        negativePrompt: negativePrompt,
        onProgress: onProgress,
      );
    }
    // 方式 2: Replicate API
    else if (config.model == 'replicate') {
      return await _callReplicateApi(
        faceImage: faceImage,
        prompt: prompt,
        onProgress: onProgress,
      );
    }
    // 方式 3: 通用 OpenAI 兼容格式
    else {
      return await _callGenericApi(
        faceImage: faceImage,
        prompt: prompt,
        negativePrompt: negativePrompt,
        onProgress: onProgress,
      );
    }
  }

  /// Stable Diffusion WebUI API
  Future<dynamic> _callStableDiffusionApi({
    required Uint8List faceImage,
    required String prompt,
    required String negativePrompt,
    void Function(double progress)? onProgress,
  }) async {
    final base64Image = base64Encode(faceImage);

    // 1. 先设置 IP-Adapter 面部参考 (如果 API 支持)
    // 2. 调用 img2img 生成
    final uri = Uri.parse('${config.apiUrl}/sdapi/v1/img2img');
    final request = http.MultipartRequest('POST', uri);

    request.fields.addAll({
      'prompt': prompt,
      'negative_prompt': negativePrompt,
      'init_images': '[$base64Image]',
      'denoising_strength': '0.6',
      'steps': '30',
      'width': '512',
      'height': '768',
      'cfg_scale': '7.5',
      'sampler_name': 'DPM++ 2M Karras',
    });

    onProgress?.call(0.4);

    final response = await request.send();
    final responseBody = await response.stream.bytesToString();

    onProgress?.call(0.8);

    if (response.statusCode == 200) {
      final json = jsonDecode(responseBody);
      return json;
    } else {
      throw Exception('API Error: ${response.statusCode} - $responseBody');
    }
  }

  /// Replicate API
  Future<dynamic> _callReplicateApi({
    required Uint8List faceImage,
    required String prompt,
    void Function(double progress)? onProgress,
  }) async {
    final base64Image = 'data:image/jpeg;base64,${base64Encode(faceImage)}';

    // 创建预测
    final uri = Uri.parse('https://api.replicate.com/v1/predictions');
    final response = await http.post(
      uri,
      headers: {
        'Authorization': 'Token ${config.apiKey}',
        'Content-Type': 'application/json',
      },
      body: jsonEncode({
        'version': 'ipercy/ip-adapter-face-id:4bbf7e0e6e8e4e0e9b1c2d3e4f5a6b7c',
        'input': {
          'image': base64Image,
          'prompt': prompt,
          'num_outputs': 1,
        },
      }),
    );

    onProgress?.call(0.4);

    if (response.statusCode == 201) {
      final prediction = jsonDecode(response.body);
      return await _pollReplicateResult(prediction['id'], onProgress);
    } else {
      throw Exception('Replicate Error: ${response.statusCode}');
    }
  }

  /// 轮询 Replicate 结果
  Future<dynamic> _pollReplicateResult(
    String predictionId, void Function(double progress)? onProgress,
  ) async {
    final uri = Uri.parse('https://api.replicate.com/v1/predictions/$predictionId');

    for (int i = 0; i < 60; i++) {
      await Future.delayed(const Duration(seconds: 2));
      onProgress?.call(0.4 + (i / 60) * 0.4);

      final response = await http.get(uri, headers: {
        'Authorization': 'Token ${config.apiKey}',
      });

      if (response.statusCode == 200) {
        final result = jsonDecode(response.body);
        if (result['status'] == 'succeeded') {
          return result;
        } else if (result['status'] == 'failed') {
          throw Exception('Generation failed');
        }
      }
    }
    throw Exception('Generation timeout');
  }

  /// 通用 API 调用
  Future<dynamic> _callGenericApi({
    required Uint8List faceImage,
    required String prompt,
    required String negativePrompt,
    void Function(double progress)? onProgress,
  }) async {
    final uri = Uri.parse(config.apiUrl);
    final request = http.MultipartRequest('POST', uri);

    request.headers['Authorization'] = 'Bearer ${config.apiKey}';
    request.files.add(http.MultipartFile.fromBytes(
      'image', faceImage, filename: 'face.jpg',
    ));
    request.fields['prompt'] = prompt;
    request.fields['negative_prompt'] = negativePrompt;

    onProgress?.call(0.3);

    final response = await request.send();
    final responseBody = await response.stream.toBytes();

    onProgress?.call(0.8);

    if (response.statusCode == 200) {
      return responseBody;
    } else {
      throw Exception('API Error: ${response.statusCode}');
    }
  }

  /// 解码 API 响应
  Uint8List _decodeResponse(dynamic response) {
    if (response is Uint8List) {
      return response;
    } else if (response is Map) {
      // Stable Diffusion 格式
      if (response.containsKey('images')) {
        final images = response['images'] as List;
        if (images.isNotEmpty) {
          return base64Decode(images[0]);
        }
      }
      // Replicate 格式
      if (response.containsKey('output')) {
        final output = response['output'];
        if (output is List && output.isNotEmpty) {
          // 下载输出图片
          return _downloadImage(output[0]);
        }
      }
    }
    throw Exception('Failed to decode API response');
  }

  /// 下载图片
  Future<Uint8List> _downloadImage(String url) async {
    final response = await http.get(Uri.parse(url));
    if (response.statusCode == 200) {
      return response.bodyBytes;
    }
    throw Exception('Failed to download image');
  }
}
