from app.services.result_service import apply_elo

def test_elo_red_win_changes():
    red_new, black_new, d_red, d_black = apply_elo(1450, 1435, 'RED_WIN')
    assert d_red > 0 and d_black < 0
