# Testing

Always add `-am` when running scoped tests, so upstream modules rebuild from
source instead of linking stale jars from `~/.m2`:

    mvn -pl Mage.Tests -am test -Dtest=HumanRecordingTest
