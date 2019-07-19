import { environment } from 'environments/environment';
import { jsonToUrlParams as toURL } from '@app/helpers/jsonToUrlParams';

const base: string = environment.baseUrl + 'launchpad/batch';

const urls: any = {
    batches: {
        // @GetMapping("/batches")
        // public BatchData.BatchesResult batches(@PageableDefault(size = 20) Pageable pageable) {
        //     return batchTopLevelService.getBatches(pageable);
        // }
        get: (data: any): string => `${base}/batches?${toURL(data)}`
    },
    batch: {
        // @PostMapping("/batches-part")
        // public BatchData.BatchesResult batchesPart(@PageableDefault(size = 20) Pageable pageable) {
        //     return batchTopLevelService.getBatches(pageable);
        // }
        get: (id: string | number): string => `${base}/batches-part/${id}`,

        // @GetMapping(value = "/batch-add")
        // public BatchData.PlansForBatchResult batchAdd() {
        //     return batchTopLevelService.getPlansForBatchResult();
        // }
        add: (): string => base + '/batch-add/',

        // @PostMapping(value = "/batch-upload-from-file")
        // public OperationStatusRest uploadFile(final MultipartFile file, Long planId) {
        //     return batchTopLevelService.batchUploadFromFile(file, planId);
        // }
        upload: (): string => '',

        // @GetMapping(value= "/batch-status/{batchId}" )
        // public BatchData.Status getProcessingResourceStatus(@PathVariable("batchId") Long batchId) {
        //     return batchTopLevelService.getProcessingResourceStatus(batchId);
        // }
        status: (id: string | number): string => `${base}/batch-status/${id}`,

        // @GetMapping("/batch-delete/{batchId}")
        // public BatchData.Status processResourceDelete(@PathVariable Long batchId) {
        //     return batchTopLevelService.getProcessingResourceStatus(batchId);
        // }
        delete2: (data: any): string => `${base}/batch-delete/?${toURL(data)}`,

        // @PostMapping("/batch-delete-commit")
        // public OperationStatusRest processResourceDeleteCommit(Long batchId) {
        //     return batchTopLevelService.processResourceDeleteCommit(batchId);
        // }
        delete: (data: any): string => `${base}/batch-delete-commit?${toURL(data)}`
    }
};

export { urls };