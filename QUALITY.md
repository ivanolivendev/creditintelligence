# Quality checks

## Java

Unit tests, ArchUnit and JaCoCo:

```powershell
cd backend-java
mvn clean verify
```

Integration tests with an isolated PostgreSQL container:

```powershell
mvn -Pintegration verify
```

Mutation testing:

```powershell
mvn -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage
```

The integration profile requires Docker Desktop to be running. Testcontainers starts
and removes its PostgreSQL container automatically.

## Python

```powershell
cd document-intelligence-python
python -m pip install -r requirements.txt -r requirements-test.txt
python -m pytest
python -m ruff check .
python -m compileall -q app tests
```

The test suite uses synthetic data only. A green test run is evidence for the
covered behaviours; it is not a substitute for reviewing coverage and mutation
reports.
