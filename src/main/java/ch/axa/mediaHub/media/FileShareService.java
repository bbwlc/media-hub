package ch.axa.mediaHub.media;

import ch.axa.mediaHub.model.SharedFile;
import ch.axa.mediaHub.repository.SharedFileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class FileShareService {

    private final SharedFileRepository sharedFileRepository;
    private final FileService fileService;

    public FileShareService(SharedFileRepository sharedFileRepository, FileService fileService) {
        this.sharedFileRepository = sharedFileRepository;
        this.fileService = fileService;
    }

    @Transactional    // should be on Service Level :)
    public SharedFile share(String owner, String filename, String sharedWith) {
        if (!fileService.listFiles(owner).contains(filename)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found.");
        }
        if (sharedWith == null) {
            if (sharedFileRepository.existsPublicShare(owner, filename)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Already shared publicly.");
            }
        } else {
            if (sharedFileRepository.existsByOwnerAndFilenameAndSharedWith(owner, filename, sharedWith)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Already shared with " + sharedWith + ".");
            }
        }
        SharedFile sharedFile = new SharedFile();
        sharedFile.setOwner(owner);
        sharedFile.setFilename(filename);
        sharedFile.setSharedWith(sharedWith);
        return sharedFileRepository.save(sharedFile);
    }

    @Transactional
    public void revoke(String owner, String filename, String sharedWith) {
        if (sharedWith == null) {
            sharedFileRepository.deletePublicShare(owner, filename);
        } else {
            sharedFileRepository.deleteByOwnerAndFilenameAndSharedWith(owner, filename, sharedWith);
        }
    }

    public List<SharedFile> getMyShares(String owner) {
        return sharedFileRepository.findByOwner(owner);
    }

    public List<SharedFile> getSharedWithMe(String username) {
        return sharedFileRepository.findAccessibleBy(username);
    }

    public boolean canAccess(String owner, String filename, String viewer) {
        return sharedFileRepository.canAccess(owner, filename, viewer);
    }
}