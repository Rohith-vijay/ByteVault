// Download Service Forwarder to Fulfillment Service (for backward compatibility)
import fulfillmentService from "./fulfillmentService";

export const downloadService = {
  // Maps to entitlements API
  getDownloadsByUser: async (_userId) => {
    return fulfillmentService.getEntitlements();
  },

  getDownloads: async () => {
    return fulfillmentService.getEntitlements();
  },

  // Maps to downloads API
  triggerFileDownload: async (id, _title) => {
    return fulfillmentService.getDownload(id);
  },

  downloadFile: async (id) => {
    return fulfillmentService.getDownload(id);
  }
};

export default downloadService;
