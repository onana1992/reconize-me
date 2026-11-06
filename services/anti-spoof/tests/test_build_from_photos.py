"""Le script plante les cinq attaques sans pièce réelle."""

from pathlib import Path

import numpy as np
from PIL import Image

from scripts.build_from_photos import write_from_photos


def test_plants_five_attacks_per_photo(tmp_path: Path) -> None:
    source = tmp_path / "photos"
    source.mkdir()
    canvas = np.full((480, 640, 3), 200, np.uint8)
    canvas[80:200, 40:180] = (90, 100, 120)
    Image.fromarray(canvas, "RGB").save(source / "card.jpeg", quality=90)

    out = tmp_path / "out"
    manifest = write_from_photos(source, out)
    assert manifest["n_physical"] == 1
    assert manifest["n_screen_recapture"] == 0
    assert manifest["counts"] == {
        "authentic": 1,
        "splicing": 1,
        "text_alteration": 1,
        "compression_inconsistency": 1,
        "screen_replay": 1,
        "printed_copy": 1,
    }
    assert (out / "manifest.json").is_file()
    assert (out / "authentic" / "000_photo.jpg").is_file()
    assert (out / "splicing" / "000_clone.jpg").is_file()
    assert (out / "screen_replay" / "000_rgb_grid.jpg").is_file()
