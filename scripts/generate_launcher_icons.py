import os
from PIL import Image

def resize_icon(source_path, target_sizes, base_res_dir):
    if not os.path.exists(source_path):
        print(f"Source file {source_path} does not exist!")
        return

    with Image.open(source_path) as img:
        # Convert to RGBA if not already
        if img.mode != 'RGBA':
            img = img.convert('RGBA')

        for density, size in target_sizes.items():
            target_dir = os.path.join(base_res_dir, f"mipmap-{density}")
            os.makedirs(target_dir, exist_ok=True)

            # Normal icon
            normal_path = os.path.join(target_dir, "ic_launcher.png")
            resized_img = img.resize((size, size), Image.Resampling.LANCZOS)
            resized_img.save(normal_path, "PNG")
            print(f"Saved normal icon ({size}x{size}) to {normal_path}")

            # Round icon
            round_path = os.path.join(target_dir, "ic_launcher_round.png")
            # For round icon, we can either use the same image or apply a circular mask
            # The source image already has a premium circular shape and dark background,
            # so saving it directly works perfectly.
            resized_img.save(round_path, "PNG")
            print(f"Saved round icon ({size}x{size}) to {round_path}")

def main():
    source_icon = r"C:\Users\Mukul Prasad\.gemini\antigravity\brain\56cad7b0-d2f0-4120-ac9f-fd10cee395c4\launcher_icon_1780292511522.png"
    base_res_dir = r"android/app/src/main/res"

    target_sizes = {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192
    }

    resize_icon(source_icon, target_sizes, base_res_dir)
    print("Launcher icon generation complete.")

if __name__ == "__main__":
    main()
