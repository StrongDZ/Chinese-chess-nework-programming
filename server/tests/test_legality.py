from app.models import GameState
from app.services.legality_service import validate_move


def test_legality_wrong_turn():
    state = GameState(game_id='g', board=[['.' for _ in range(9)] for __ in range(10)], side_to_move='red')
    ok, reason = validate_move(state, ((0,0),(0,1)), 'black')
    assert not ok and reason == 'WRONG_TURN'
