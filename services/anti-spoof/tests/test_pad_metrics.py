"""ISO/IEC 30107-3 : APCER, BPCER, EER, critère §12."""

from scripts.pad_metrics import (
    DECLINE_AT,
    REVIEW_AT,
    apcer_at_bpcer,
    eer,
    open_for_decline,
    rates,
    report_alert,
)


def test_perfect_separation_at_both_thresholds() -> None:
    bona = [0.0] * 20
    attack = [1.0] * 20
    apcer, bpcer = rates(bona, attack, REVIEW_AT)
    assert apcer == 0.0
    assert bpcer == 0.0
    apcer, bpcer = rates(bona, attack, DECLINE_AT)
    assert apcer == 0.0
    assert bpcer == 0.0
    assert open_for_decline(apcer, bpcer)
    assert eer(bona, attack) == 0.0


def test_one_false_alarm_in_twenty_is_five_percent_bpcer() -> None:
    bona = [0.0] * 19 + [0.9]
    attack = [0.9] * 20
    _apcer, bpcer = rates(bona, attack, REVIEW_AT)
    assert bpcer == 0.05
    assert apcer_at_bpcer(bona, attack) == 0.0


def test_missed_attacks_raise_apcer() -> None:
    bona = [0.1] * 10
    attack = [0.9] * 9 + [0.2]
    apcer, bpcer = rates(bona, attack, DECLINE_AT)
    assert apcer == 0.1
    assert bpcer == 0.0
    assert open_for_decline(apcer, bpcer)


def test_section_twelve_stays_closed_when_false_alarms_exceed_five_percent() -> None:
    bona = [0.9] * 2 + [0.0] * 8
    attack = [0.9] * 10
    apcer, bpcer = rates(bona, attack, DECLINE_AT)
    assert apcer == 0.0
    assert bpcer == 0.2
    assert not open_for_decline(apcer, bpcer)
    row = report_alert(bona, attack)
    assert row["open_for_decline"] is False
    assert row["n_bona_fide"] == 10
    assert row["n_attack"] == 10
