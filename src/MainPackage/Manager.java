package MainPackage;

public class Manager {

	public static void main(String[] args) {		
        try {
            final S3Object f = getObject(bucket, k);
            final BufferedInputStream i = new BufferedInputStream(f.getObjectContent());
            final StringBuilder s = new StringBuilder();
            final byte[] b = new byte[1024];
            for (int n = i.read(b); n != -1; n = i.read(b)) {
                s.append(new String(b, 0, n));
            }
            return s.toString();
        } catch (Exception e) {
            log("Cannot get " + bucket + "/" + k + " from S3 because " + e);
        }
	}
}
