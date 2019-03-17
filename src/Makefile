all:
	${CHECKERFRAMEWORK}/checker/bin/javac `find . -name '*.java'`

# This target only works after running "make all"
check:
	${CHECKERFRAMEWORK}/checker/bin/javac -processor index io/nayuki/deflate/DeflaterOutputStream.java io/nayuki/deflate/MarkableFileInputStream.java gunzip.java
