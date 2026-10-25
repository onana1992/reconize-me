package com.kyc.services.aml;

import com.kyc.enums.AmlCategory;
import java.util.List;

public final class FixtureCorpus {

    public static final String VERSION = "fixture-1";

    private FixtureCorpus() {}

    public static List<ListedPerson> fixture1() {
        return List.of(
                new ListedPerson(
                        "fx-sanction-ivan",
                        "Ivan Sanctionexample",
                        List.of(),
                        "1971-04-12",
                        null,
                        "X1234567",
                        List.of(AmlCategory.SANCTION),
                        "fixture_sanctions"),
                new ListedPerson(
                        "fx-pep-nora",
                        "Nora Potentialexample",
                        List.of("Nora P Example"),
                        "1982-11-03",
                        "FR",
                        null,
                        List.of(AmlCategory.PEP),
                        "fixture_peps"),
                new ListedPerson(
                        "fx-rca-leo",
                        "Leo Potentialexample",
                        List.of(),
                        "2005-01-20",
                        "FR",
                        null,
                        List.of(AmlCategory.RCA),
                        "fixture_peps"),
                new ListedPerson(
                        "fx-debar-mina",
                        "Mina Debarexample",
                        List.of(),
                        "1990",
                        "CA",
                        null,
                        List.of(AmlCategory.DEBARMENT),
                        "fixture_debarment"),
                new ListedPerson(
                        "fx-sanction-julien",
                        "Julien Mercier",
                        List.of(),
                        "1971-04-12",
                        "FR",
                        null,
                        List.of(AmlCategory.SANCTION),
                        "fixture_sanctions"),
                new ListedPerson(
                        "fx-pep-camille",
                        "Camille Bernard",
                        List.of(),
                        "1982-11-03",
                        "FR",
                        null,
                        List.of(AmlCategory.PEP),
                        "fixture_peps"),
                new ListedPerson(
                        "fx-rca-lucas",
                        "Lucas Bernard",
                        List.of(),
                        "2005-01-20",
                        "FR",
                        null,
                        List.of(AmlCategory.RCA),
                        "fixture_peps"),
                new ListedPerson(
                        "fx-debar-sophie",
                        "Sophie Tremblay",
                        List.of(),
                        "1990",
                        "CA",
                        null,
                        List.of(AmlCategory.DEBARMENT),
                        "fixture_debarment"));
    }
}
