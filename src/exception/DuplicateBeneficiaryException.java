package exception;

import model.person.Beneficiary;

public class DuplicateBeneficiaryException extends Exception {
    private final String candidateId;
    private final Beneficiary existing;

    public DuplicateBeneficiaryException(String candidateId, Beneficiary existing) {
        super(candidateId + " matches " + existing.getId() + " (" + existing.getName() + ") by phone");
        this.candidateId = candidateId;
        this.existing = existing;
    }

    public String getCandidateId() { return candidateId; }
    public Beneficiary getExisting() { return existing; }
}
