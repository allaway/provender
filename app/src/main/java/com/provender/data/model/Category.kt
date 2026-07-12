package com.provender.data.model

/** Item categories from SPEC.md §3.3. */
enum class Category(val label: String) {
    PRODUCE("Produce"),
    DAIRY("Dairy"),
    PROTEIN("Protein"),
    GRAIN("Grain"),
    CANNED("Canned"),
    CONDIMENT("Condiment"),
    SPICE("Spice"),
    SNACK("Snack"),
    BAKING("Baking"),
    BEVERAGE("Beverage"),
    FROZEN("Frozen"),
    OTHER("Other"),
}
