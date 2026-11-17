package es.uma.urbanpulse.incident;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * A page request that fetches one extra row to detect hasMore, but whose offset
 * advances by the page size that is shown. With a plain PageRequest the offset
 * would advance by the larger fetch size and one incident would be skipped at
 * every page boundary; here the extra row overlaps the next page instead.
 */
final class FetchAheadPageRequest extends PageRequest {

	private FetchAheadPageRequest(int page, int fetchSize) {
		super(page, fetchSize, Sort.unsorted());
	}

	static FetchAheadPageRequest fetchAhead(int page, int shownSize) {
		return new FetchAheadPageRequest(page, shownSize + 1);
	}

	@Override
	public long getOffset() {
		return (long) getPageNumber() * (getPageSize() - 1);
	}

	@Override
	public PageRequest next() {
		return new FetchAheadPageRequest(getPageNumber() + 1, getPageSize());
	}

	@Override
	public PageRequest previous() {
		return new FetchAheadPageRequest(Math.max(0, getPageNumber() - 1), getPageSize());
	}

	@Override
	public PageRequest first() {
		return new FetchAheadPageRequest(0, getPageSize());
	}
}
