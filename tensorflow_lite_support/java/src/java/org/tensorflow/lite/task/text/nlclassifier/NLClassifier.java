/* Copyright 2020 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package org.tensorflow.lite.task.text.nlclassifier;

import android.content.Context;
import com.google.auto.value.AutoValue;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.util.List;
import org.tensorflow.lite.annotations.UsedByReflection;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.core.BaseTaskApi;
import org.tensorflow.lite.task.core.TaskJniUtils;
import org.tensorflow.lite.task.core.TaskJniUtils.EmptyHandleProvider;

/**
 * Classifier API for natural language classification tasks, categorizes string into different
 * classes.
 *
 * <p>The API expects a TFLite model with the following input/output tensor:
 *
 * <ul>
 *   <li>Input tensor (kTfLiteString)
 *       <ul>
 *         <li>input of the model, accepts a string.
 *       </ul>
 *   <li>Output score tensor (kTfLiteUInt8/kTfLiteInt8/kTfLiteInt16/kTfLiteFloat32/kTfLiteFloat64)
 *       <ul>
 *         <li>output scores for each class, if type is one of the Int types, dequantize it.
 *         <li>can have an optional associated file in metadata for labels, the file should be a
 *             plain text file with one label per line, the number of labels should match the number
 *             of categories the model outputs. Output label tensor: optional (kTfLiteString) -
 *             output classname for each class, should be of the same length with scores. If this
 *             tensor is not present, the API uses score indices as classnames. - will be ignored if
 *             output score tensor already has an associated label file.
 *       </ul>
 *   <li>Optional Output label tensor (kTfLiteString/kTfLiteInt32)
 *       <ul>
 *         <li>output classname for each class, should be of the same length with scores. If this
 *             tensor is not present, the API uses score indices as classnames.
 *         <li>will be ignored if output score tensor already has an associated labe file.
 *       </ul>
 * </ul>
 *
 * <p>By default the API tries to find the input/output tensors with default configurations in
 * {@link NLClassifierOptions}, with tensor name prioritized over tensor index. The option is
 * configurable for different TFLite models.
 */
public class NLClassifier extends BaseTaskApi {

  /** Options to identify input and output tensors of the model. */
  @AutoValue
  @UsedByReflection("nl_classifier_jni.cc")
  public abstract static class NLClassifierOptions {
    private static final int DEFAULT_INPUT_TENSOR_INDEX = 0;
    private static final int DEFAULT_OUTPUT_SCORE_TENSOR_INDEX = 0;
    // By default there is no output label tensor. The label file can be attached
    // to the output score tensor metadata.
    private static final int DEFAULT_OUTPUT_LABEL_TENSOR_INDEX = -1;
    private static final String DEFAULT_INPUT_TENSOR_NAME = "INPUT";
    private static final String DEFAULT_OUTPUT_SCORE_TENSOR_NAME = "OUTPUT_SCORE";
    private static final String DEFAULT_OUTPUT_LABEL_TENSOR_NAME = "OUTPUT_LABEL";

    @UsedByReflection("nl_classifier_jni.cc")
    abstract int inputTensorIndex();

    @UsedByReflection("nl_classifier_jni.cc")
    abstract int outputScoreTensorIndex();

    @UsedByReflection("nl_classifier_jni.cc")
    abstract int outputLabelTensorIndex();

    @UsedByReflection("nl_classifier_jni.cc")
    abstract String inputTensorName();

    @UsedByReflection("nl_classifier_jni.cc")
    abstract String outputScoreTensorName();

    @UsedByReflection("nl_classifier_jni.cc")
    abstract String outputLabelTensorName();

    public static Builder builder() {
      return new AutoValue_NLClassifier_NLClassifierOptions.Builder()
          .setInputTensorIndex(DEFAULT_INPUT_TENSOR_INDEX)
          .setOutputScoreTensorIndex(DEFAULT_OUTPUT_SCORE_TENSOR_INDEX)
          .setOutputLabelTensorIndex(DEFAULT_OUTPUT_LABEL_TENSOR_INDEX)
          .setInputTensorName(DEFAULT_INPUT_TENSOR_NAME)
          .setOutputScoreTensorName(DEFAULT_OUTPUT_SCORE_TENSOR_NAME)
          .setOutputLabelTensorName(DEFAULT_OUTPUT_LABEL_TENSOR_NAME);
    }

    /** Builder for {@link NLClassifierOptions}. */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setInputTensorIndex(int value);

      public abstract Builder setOutputScoreTensorIndex(int value);

      public abstract Builder setOutputLabelTensorIndex(int value);

      public abstract Builder setInputTensorName(String value);

      public abstract Builder setOutputScoreTensorName(String value);

      public abstract Builder setOutputLabelTensorName(String value);

      public abstract NLClassifierOptions build();
    }
  }

  private static final String NL_CLASSIFIER_NATIVE_LIBNAME = "nl_classifier_jni";

  /**
   * Constructor to initialize the JNI with a pointer from C++.
   *
   * @param nativeHandle a pointer referencing memory allocated in C++.
   */
  protected NLClassifier(long nativeHandle) {
    super(nativeHandle);
  }

  /**
   * Create {@link NLClassifier} from default {@link NLClassifierOptions}.
   *
   * @param context Android context.
   * @param pathToModel Path to the classification model.
   * @return {@link NLClassifier} instance.
   * @throws IOException If model file fails to load.
   */
  public static NLClassifier createFromFile(Context context, String pathToModel)
      throws IOException {
    return createFromFileAndOptions(context, pathToModel, NLClassifierOptions.builder().build());
  }

  /**
   * Create {@link NLClassifier} from {@link NLClassifierOptions}.
   *
   * @param context Android context
   * @param pathToModel Path to the classification model.
   * @param options Configurations for the model.
   * @return {@link NLClassifier} instance.
   * @throws IOException If model file fails to load.
   */
  public static NLClassifier createFromFileAndOptions(
      Context context, String pathToModel, NLClassifierOptions options) throws IOException {
    return createFromBufferAndOptions(TaskJniUtils.loadMappedFile(context, pathToModel), options);
  }

  /**
   * Create {@link NLClassifier} with {@link MappedByteBuffer} from {@link NLClassifierOptions}.
   *
   * @param modelBuffer In memory buffer of the classification model.
   * @param options Configurations for the model.
   * @return {@link NLClassifier} instance.
   */
  public static NLClassifier createFromBufferAndOptions(
      final MappedByteBuffer modelBuffer, final NLClassifierOptions options) {
    return new NLClassifier(
        TaskJniUtils.createHandleFromLibrary(
            new EmptyHandleProvider() {
              @Override
              public long createHandle() {
                return initJniWithByteBuffer(options, modelBuffer);
              }
            },
            NL_CLASSIFIER_NATIVE_LIBNAME));
  }

  /**
   * Perform classification on a string input, returns classified {@link Category}s.
   *
   * @param text input text to the model.
   * @return A list of Category results.
   */
  public List<Category> classify(String text) {
    return classifyNative(getNativeHandle(), text);
  }

  private static native long initJniWithByteBuffer(
      NLClassifierOptions options, ByteBuffer modelBuffer);

  private static native List<Category> classifyNative(long nativeHandle, String text);
}