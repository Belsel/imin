import { Link } from 'react-router'
import type { GroupRecommendationResponse, GroupResponse } from '../lib/apiClient'

/**
 * Card-style list item linking to a group's detail page. Shared by
 * GroupsListPage (search/recommendations/your groups) and HomePage
 * (dashboard "Your Groups" section) so the markup isn't duplicated.
 */
export default function GroupListItem({
  group,
  distanceKm,
}: {
  group: GroupResponse | GroupRecommendationResponse
  distanceKm?: number
}) {
  return (
    <li>
      <Link
        to={`/groups/${group.id}`}
        className="block rounded-2xl border border-border bg-surface p-4 shadow-sm transition-colors motion-safe:hover:border-primary/60 motion-safe:hover:shadow"
      >
        <div className="flex items-center justify-between">
          <span className="font-medium text-text font-body">{group.name}</span>
          <span className="text-sm text-text-muted font-body">
            {group.memberCount} member{group.memberCount === 1 ? '' : 's'}
          </span>
        </div>
        {group.description && <p className="mt-1 text-sm text-text-muted font-body">{group.description}</p>}
        <div className="mt-2 flex flex-wrap gap-2">
          {group.categories.map((category) => (
            <span
              key={category.id}
              className="rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary"
            >
              {category.name}
            </span>
          ))}
        </div>
        {distanceKm !== undefined && (
          <p className="mt-2 text-xs text-text-muted font-body">{distanceKm.toFixed(1)} km away</p>
        )}
      </Link>
    </li>
  )
}
