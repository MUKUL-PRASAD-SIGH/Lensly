import os
import sys

def main():
    # Define paths
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    assets_dir = os.path.join(base_dir, "android", "app", "src", "main", "assets")
    os.makedirs(assets_dir, exist_ok=True)
    
    vocab_path = os.path.join(assets_dir, "vocab.txt")
    tflite_path = os.path.join(assets_dir, "minilm.tflite")

    print("--- Lensly MiniLM Export Pipeline ---")
    print(f"Target Assets Directory: {assets_dir}")

    try:
        import tensorflow as tf
        from transformers import AutoTokenizer, TFAutoModel
    except ImportError as e:
        print(f"Error importing dependencies: {e}")
        print("Please ensure you have run: pip install transformers tensorflow torch tf-keras")
        sys.exit(1)

    model_name = "sentence-transformers/all-MiniLM-L6-v2"
    
    # 1. Load and save tokenizer vocabulary
    print(f"\n[1/3] Loading tokenizer for {model_name}...")
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    
    print(f"Saving vocabulary to: {vocab_path}")
    vocab_files = tokenizer.save_vocabulary(assets_dir)
    # If the file saved is named vocab.txt or otherwise, make sure it is exactly vocab.txt
    saved_vocab_file = vocab_files[0] if isinstance(vocab_files, (list, tuple)) else vocab_files
    if os.path.exists(saved_vocab_file) and saved_vocab_file != vocab_path:
        os.rename(saved_vocab_file, vocab_path)
    print("Vocabulary successfully saved!")

    # 2. Load and build TF model
    print(f"\n[2/3] Loading TensorFlow transformer model for {model_name}...")
    # Load PyTorch weights and convert to TF to avoid downloading TF weights if only PyTorch is cached
    transformer = TFAutoModel.from_pretrained(model_name, from_pt=True)

    # Wrap in tf.Module to perform Mean Pooling + L2 Normalization on-device
    class MiniLMEmbedder(tf.Module):
        def __init__(self, transformer):
            super().__init__()
            self.transformer = transformer

        @tf.function(input_signature=[
            tf.TensorSpec(shape=[None, None], dtype=tf.int32, name="input_ids"),
            tf.TensorSpec(shape=[None, None], dtype=tf.int32, name="attention_mask")
        ])
        def __call__(self, input_ids, attention_mask):
            outputs = self.transformer(input_ids=input_ids, attention_mask=attention_mask)
            token_embeddings = outputs.last_hidden_state
            
            # Mean Pooling: Average token embeddings across non-masked positions
            input_mask_expanded = tf.cast(tf.expand_dims(attention_mask, -1), tf.float32)
            sum_embeddings = tf.reduce_sum(token_embeddings * input_mask_expanded, axis=1)
            sum_mask = tf.reduce_sum(input_mask_expanded, axis=1)
            sum_mask = tf.maximum(sum_mask, 1e-9)
            embeddings = sum_embeddings / sum_mask
            
            # L2 Normalization: Makes cosine similarity equivalent to simple dot product
            normalized = tf.linalg.l2_normalize(embeddings, axis=1)
            return normalized

    embedder = MiniLMEmbedder(transformer)

    # 3. Convert to TFLite
    print("\n[3/3] Converting TensorFlow model to TFLite (with dynamic-range quantization)...")
    concrete_func = embedder.__call__.get_concrete_function(
        tf.TensorSpec(shape=[None, None], dtype=tf.int32, name="input_ids"),
        tf.TensorSpec(shape=[None, None], dtype=tf.int32, name="attention_mask")
    )
    
    converter = tf.lite.TFLiteConverter.from_concrete_functions([concrete_func], embedder)
    
    # Apply dynamic-range quantization to reduce model size from ~90MB to ~22MB
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    
    # Ensure support for Select TF ops in case some layers require them,
    # but prioritize TFLite builtins for performance.
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,
        tf.lite.OpsSet.SELECT_TF_OPS
    ]
    
    tflite_model = converter.convert()

    with open(tflite_path, "wb") as f:
        f.write(tflite_model)
        
    print(f"\nSuccess! Quantized TFLite model written to: {tflite_path}")
    print(f"Model File Size: {os.path.getsize(tflite_path) / (1024 * 1024):.2f} MB")
    print("Export pipeline finished successfully.")

if __name__ == "__main__":
    main()
