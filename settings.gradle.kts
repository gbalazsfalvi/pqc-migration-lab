rootProject.name = "pqc-migration-lab"

// Protocol, application rules, adapters, and local executables remain separate modules.
include("lab-core", "crypto-jca", "dsse-json", "technical-probe", "integration-core", "integration-app", "benchmarks")
