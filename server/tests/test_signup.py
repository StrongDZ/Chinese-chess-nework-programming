from app.repository.users import UsersRepo
from app.repository.stats import StatsRepo
from app.services.signup_service import signup


def test_signup_success():
    urepo, srepo = UsersRepo(), StatsRepo()
    ok, resp = signup(urepo, srepo, 'User_123', 'u@example.com', 'Abcdef12')
    assert ok and resp['status'] == 'SUCCESS'


def test_signup_duplicate():
    urepo, srepo = UsersRepo(), StatsRepo()
    signup(urepo, srepo, 'User_123', 'u@example.com', 'Abcdef12')
    ok, resp = signup(urepo, srepo, 'User_123', 'u@example.com', 'Abcdef12')
    assert not ok and resp['status'] == 'FAIL'
